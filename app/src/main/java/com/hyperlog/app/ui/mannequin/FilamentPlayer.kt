package com.hyperlog.app.ui.mannequin

import android.content.Context
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.SwapChain
import com.google.android.filament.ToneMapper
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.Animator
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.hyperlog.app.mannequin.BENCH_BACK
import com.hyperlog.app.mannequin.RepTimeline
import com.hyperlog.app.mannequin.StageCamera
import com.hyperlog.app.mannequin.benchBackTransform
import com.hyperlog.app.ui.theme.ClayColours
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** The stage's vertical field of view, in degrees: a long lens, so the body isn't distorted. */
private const val FieldOfView = 26.0

/**
 * The key light's place, kept relative to the camera so every turn is lit alike: this far round
 * to the camera's left, in degrees, and this high. Lit from the side, the clay shows its form; a
 * light from the camera's own direction flattens it.
 */
private const val KeyTurn = 55.0
private const val KeyHeight = 50.0
private const val KeyIntensity = 2.8f

/** The sky's irradiance, 2-band SH: the even part, and how much brighter from above than below. */
private const val SkyBase = 0.36f
private const val SkyTilt = 0.26f

/**
 * Plays a mannequin clip with Filament, in a [TextureView] that lets the stage's own colour show
 * through. One engine per stage, released with it. The clip's one animation is the rep's lowering;
 * [RepTimeline] plays it forward and back.
 */
internal class FilamentPlayer(context: Context) : MannequinPlayer {
    override val view = TextureView(context)
    override var onShown: () -> Unit = {}

    private val engine = Engine.create()
    private val renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val filamentView = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera = engine.createCamera(cameraEntity)
    private val sun = EntityManager.get().create()
    private val materials = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materials, EntityManager.get())
    private val resourceLoader = ResourceLoader(engine, true)
    private val grading = ColorGrading.Builder().toneMapper(ToneMapper.Linear()).build(engine)
    private val sky: IndirectLight
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val choreographer = Choreographer.getInstance()

    private var swapChain: SwapChain? = null
    private var asset: FilamentAsset? = null
    private var animator: Animator? = null
    private var duration = 1f
    private var timeline = RepTimeline(lowering = 1f, bottom = 0f, lifting = 1f, top = 0f)
    private var stage: StageCamera? = null
    private var azimuth = 0f
    private var aspect = 1.0
    private var colours: ClayColours? = null
    private var playing = false
    private var still = false
    // How far into the loop it has played, so a pause behind another app carries on where it was.
    private var playedNanos = 0L
    private var lastFrameNanos = 0L
    // A still stage draws only when something changed.
    private var dirty = true
    private var shown = false
    // The bench angle to stand the back pad at, kept for the clips loaded after it.
    private var bench: Pair<Int, List<Float>>? = null
    // The files the loaded clip reads by name, kept for as long as the player.
    private var shared: Map<String, ByteBuffer> = emptyMap()

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!playing) return
            choreographer.postFrameCallback(this)
            draw(frameTimeNanos)
        }
    }

    init {
        renderer.clearOptions = Renderer.ClearOptions().apply { clear = true }
        filamentView.scene = scene
        filamentView.camera = camera
        filamentView.blendMode = View.BlendMode.TRANSLUCENT
        filamentView.colorGrading = grading
        filamentView.multiSampleAntiAliasingOptions = View.MultiSampleAntiAliasingOptions().apply { enabled = true; sampleCount = 4 }
        filamentView.setShadowingEnabled(true)
        // Exposure 1: a light's intensity is the brightness it gives.
        camera.setExposure(1f)
        // Clay: a key light high on the camera's left (placed with the camera), and a hemisphere of
        // sky over a grey floor that fills the shade.
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .direction(0f, -1f, 0f)
            .intensity(KeyIntensity)
            .castShadows(true)
            .build(engine, sun)
        scene.addEntity(sun)
        sky = IndirectLight.Builder()
            .irradiance(2, floatArrayOf(SkyBase, SkyBase, SkyBase + 0.01f, SkyTilt, SkyTilt, SkyTilt - 0.01f, 0f, 0f, 0f, 0f, 0f, 0f))
            .intensity(1f)
            .build(engine)
        scene.indirectLight = sky
        uiHelper.isOpaque = false
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let(engine::destroySwapChain)
                swapChain = engine.createSwapChain(surface, uiHelper.swapChainFlags)
                dirty = true
            }

            override fun onDetachedFromSurface() {
                swapChain?.let {
                    engine.destroySwapChain(it)
                    engine.flushAndWait()
                    swapChain = null
                }
            }

            override fun onResized(width: Int, height: Int) {
                filamentView.viewport = Viewport(0, 0, width, height)
                aspect = width.toDouble() / height.coerceAtLeast(1)
                dirty = true
            }
        }
        uiHelper.attachTo(view)
    }

    override fun load(glb: ByteBuffer, resources: Map<String, ByteBuffer>, camera: StageCamera, tempo: RepTimeline) {
        val loaded = assetLoader.createAsset(glb) ?: return
        // Another clip replaces the one before, and fades in again.
        asset?.let {
            scene.removeEntities(it.entities)
            assetLoader.destroyAsset(it)
        }
        shown = false
        timeline = tempo
        playedNanos = 0L
        // The clip reads the mannequin's mesh and skin from a file it shares with every other clip.
        // The engine uploads them from that buffer when it next flushes, at the stage's first
        // frame, and nothing else keeps the buffer alive, so the player holds it: freed earlier,
        // the body uploads garbage and doesn't show.
        shared = resources
        resources.forEach { (name, data) -> resourceLoader.addResourceData(name, data) }
        resourceLoader.loadResources(loaded)
        resourceLoader.evictResourceData()
        loaded.releaseSourceData()
        scene.addEntities(loaded.entities)
        val renderables = engine.renderableManager
        loaded.renderableEntities.forEach { e ->
            val instance = renderables.getInstance(e)
            // The floor shadow is drawn, not cast.
            val shadow = loaded.getName(e) == "shadow"
            renderables.setCastShadows(instance, !shadow)
            renderables.setReceiveShadows(instance, !shadow)
        }
        asset = loaded
        applyBench()
        animator = loaded.instance.animator.also { duration = it.getAnimationDuration(0) }
        stage = camera
        azimuth = camera.azimuth
        applyColours()
        dirty = true
    }

    override fun play(still: Boolean) {
        this.still = still
        if (playing) return
        playing = true
        lastFrameNanos = 0L
        dirty = true
        choreographer.postFrameCallback(frame)
    }

    override fun pause() {
        playing = false
        choreographer.removeFrameCallback(frame)
    }

    override fun turn(degrees: Float) {
        azimuth -= degrees
        dirty = true
    }

    override fun resetView() {
        stage?.let { azimuth = it.azimuth }
        dirty = true
    }

    override fun setBench(degrees: Int, hinge: List<Float>) {
        bench = degrees to hinge
        applyBench()
        dirty = true
    }

    /** Turns the clip's bench back to the angle: a node the clip bakes without animation, so nothing else moves it. */
    private fun applyBench() {
        val (degrees, hinge) = bench ?: return
        val loaded = asset ?: return
        val back = loaded.getFirstEntityByName(BENCH_BACK).takeIf { it != 0 } ?: return
        val transforms = engine.transformManager
        transforms.setTransform(transforms.getInstance(back), benchBackTransform(degrees, hinge))
    }

    override fun setColours(colours: ClayColours) {
        this.colours = colours
        applyColours()
        dirty = true
    }

    private fun applyColours() {
        val c = colours ?: return
        asset?.instance?.materialInstances?.forEach { m ->
            val colour = when (m.name) {
                "body" -> c.body
                "joint" -> c.joint
                "bar" -> c.bar
                "plate" -> c.plate
                "shadow" -> {
                    m.setParameter("baseColorFactor", Colors.RgbaType.SRGB, 0f, 0f, 0f, c.shadow)
                    return@forEach
                }
                else -> return@forEach
            }
            m.setParameter("baseColorFactor", Colors.RgbaType.SRGB, colour.red, colour.green, colour.blue, 1f)
        }
    }

    private fun draw(frameTimeNanos: Long) {
        val stage = stage ?: return
        val animator = animator ?: return
        if (still && !dirty) return
        if (lastFrameNanos != 0L) playedNanos += frameTimeNanos - lastFrameNanos
        lastFrameNanos = frameTimeNanos
        // Pose the clip only for a frame that will be drawn: the bone matrices go to the GPU as
        // commands, and a stage with no surface yet (the gallery's next page, made ahead of time)
        // or a frame the renderer skips would queue them without ever sending them, until the
        // engine's command buffer overflows.
        val chain = swapChain ?: return
        if (!uiHelper.isReadyToRender || !renderer.beginFrame(chain, frameTimeNanos)) return
        val position = if (still) 1f else timeline.position(playedNanos / 1e9f)
        // The animator wraps a time at its end round to the start, so the bottom stays a hair short of it.
        animator.applyAnimation(0, (position * duration).coerceAtMost(duration * 0.9999f))
        animator.updateBoneMatrices()
        placeCamera(stage)
        renderer.render(filamentView)
        renderer.endFrame()
        dirty = false
        if (!shown) {
            shown = true
            onShown()
        }
    }

    private fun placeCamera(stage: StageCamera) {
        val distance = stage.halfHeight / tan(Math.toRadians(FieldOfView / 2))
        val az = Math.toRadians(azimuth.toDouble())
        val el = Math.toRadians(stage.elevation.toDouble())
        val (x, y, z) = stage.target.map(Float::toDouble)
        camera.setProjection(FieldOfView, aspect, 0.05, 50.0, Camera.Fov.VERTICAL)
        camera.lookAt(
            x + distance * cos(el) * sin(az), y + distance * sin(el), z + distance * cos(el) * cos(az),
            x, y, z,
            0.0, 1.0, 0.0,
        )
        // The key light travels from its place toward the stage.
        val keyAz = az - Math.toRadians(KeyTurn)
        val keyEl = Math.toRadians(KeyHeight)
        engine.lightManager.setDirection(
            engine.lightManager.getInstance(sun),
            (-cos(keyEl) * sin(keyAz)).toFloat(), (-sin(keyEl)).toFloat(), (-cos(keyEl) * cos(keyAz)).toFloat(),
        )
    }

    override fun release() {
        pause()
        uiHelper.detach()
        asset?.let {
            scene.removeEntities(it.entities)
            assetLoader.destroyAsset(it)
        }
        materials.destroyMaterials()
        materials.destroy()
        assetLoader.destroy()
        resourceLoader.destroy()
        engine.destroyEntity(sun)
        EntityManager.get().destroy(sun)
        engine.destroyIndirectLight(sky)
        engine.destroyColorGrading(grading)
        engine.destroyRenderer(renderer)
        engine.destroyView(filamentView)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        engine.destroy()
    }

    companion object {
        init {
            Gltfio.init()
        }
    }
}
