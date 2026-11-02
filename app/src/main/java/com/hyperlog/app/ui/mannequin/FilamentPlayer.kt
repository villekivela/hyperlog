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
import com.hyperlog.app.mannequin.RepTimeline
import com.hyperlog.app.mannequin.StageCamera
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** The stage's vertical field of view, in degrees: a long lens, so the body isn't distorted. */
private const val FieldOfView = 26.0

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
    private val timeline = RepTimeline.Default

    private var swapChain: SwapChain? = null
    private var asset: FilamentAsset? = null
    private var animator: Animator? = null
    private var duration = 1f
    private var stage: StageCamera? = null
    private var azimuth = 0f
    private var aspect = 1.0
    private var colours: MannequinColours? = null
    private var playing = false
    private var still = false
    private var startNanos = 0L
    // A still stage draws only when something changed.
    private var dirty = true
    private var shown = false

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
        // Clay: a soft sun from above, front and right, and a hemisphere of sky over a grey floor.
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .direction(-2.2f, -4f, -2.6f)
            .intensity(2.4f)
            .castShadows(true)
            .build(engine, sun)
        scene.addEntity(sun)
        sky = IndirectLight.Builder()
            .irradiance(2, floatArrayOf(0.44f, 0.44f, 0.45f, 0.31f, 0.31f, 0.30f, 0f, 0f, 0f, 0f, 0f, 0f))
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

    override fun load(glb: ByteBuffer, camera: StageCamera) {
        val loaded = assetLoader.createAsset(glb) ?: return
        resourceLoader.loadResources(loaded)
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
        startNanos = 0L
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

    override fun colours(colours: MannequinColours) {
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
                else -> return@forEach
            }
            m.setParameter("baseColorFactor", Colors.RgbaType.SRGB, colour.red, colour.green, colour.blue, 1f)
        }
    }

    private fun draw(frameTimeNanos: Long) {
        val stage = stage ?: return
        val animator = animator ?: return
        if (still && !dirty) return
        if (startNanos == 0L) startNanos = frameTimeNanos
        val position = if (still) 1f else timeline.position((frameTimeNanos - startNanos) / 1e9f)
        // The animator wraps a time at its end round to the start, so the bottom stays a hair short of it.
        animator.applyAnimation(0, (position * duration).coerceAtMost(duration * 0.9999f))
        animator.updateBoneMatrices()
        placeCamera(stage)
        val chain = swapChain ?: return
        if (!uiHelper.isReadyToRender || !renderer.beginFrame(chain, frameTimeNanos)) return
        renderer.render(filamentView)
        renderer.endFrame()
        dirty = false
        if (!shown) {
            shown = true
            onShown()
        }
    }

    private fun placeCamera(stage: StageCamera) {
        val distance = stage.half / tan(Math.toRadians(FieldOfView / 2))
        val az = Math.toRadians(azimuth.toDouble())
        val el = Math.toRadians(stage.elevation.toDouble())
        val (x, y, z) = stage.target.map(Float::toDouble)
        camera.setProjection(FieldOfView, aspect, 0.05, 50.0, Camera.Fov.VERTICAL)
        camera.lookAt(
            x + distance * cos(el) * sin(az), y + distance * sin(el), z + distance * cos(el) * cos(az),
            x, y, z,
            0.0, 1.0, 0.0,
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
