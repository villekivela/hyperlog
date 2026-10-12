package com.hyperlog.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TechniqueVideoTest {
    @Test
    fun readsTheVideoAndItsTimestampFromTheUsualLinkShapes() {
        assertEquals(TechniqueVideo("SJqInYJcg5k", 653), TechniqueVideo.parse("https://youtu.be/SJqInYJcg5k?t=653"))
        assertEquals(TechniqueVideo("qmyToaabqdQ", null), TechniqueVideo.parse("https://youtu.be/qmyToaabqdQ"))
        assertEquals(TechniqueVideo("ebyOmAZUqzo", 698), TechniqueVideo.parse("https://www.youtube.com/watch?v=ebyOmAZUqzo&t=698s"))
        assertEquals(TechniqueVideo("e_48W0vlU58", 90), TechniqueVideo.parse("https://m.youtube.com/watch?v=e_48W0vlU58&t=1m30s"))
        assertEquals(TechniqueVideo("98HWfiRonkE", null), TechniqueVideo.parse("https://youtube.com/shorts/98HWfiRonkE?si=x"))
        assertEquals(TechniqueVideo("qmyToaabqdQ", 3723), TechniqueVideo.parse(" youtu.be/qmyToaabqdQ?t=1h2m3s "))
    }

    @Test
    fun refusesWhatIsNotAYouTubeVideo() {
        assertNull(TechniqueVideo.parse("https://vimeo.com/12345"))
        assertNull(TechniqueVideo.parse("bench press"))
        assertNull(TechniqueVideo.parse("https://youtu.be/"))
    }

    @Test
    fun opensInYouTubeAtTheTimestamp() {
        assertEquals("https://www.youtube.com/watch?v=SJqInYJcg5k&t=653s", TechniqueVideo("SJqInYJcg5k", 653).watchUrl)
        assertEquals("https://www.youtube.com/watch?v=qmyToaabqdQ", TechniqueVideo("qmyToaabqdQ", null).watchUrl)
        assertEquals("10:53", TechniqueVideo("x", 653).startLabel)
        assertEquals("1:02:03", TechniqueVideo("x", 3723).startLabel)
    }
}
