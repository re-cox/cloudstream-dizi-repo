package com.dizi

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray

class PageJsonTest {
    @Test fun rscChunkDecodesEscapedStrings() {
        val payload = "{\"episodes\":[{\"s\":1,\"n\":1,\"title\":\"A {title}\",\"cast\":[]}]}"
        val quoted = JSONArray().put(payload).toString().removePrefix("[").removeSuffix("]")
        val html = "<script>self.__next_f.push([1,$quoted])</script>"
        assertEquals(payload, PageJson.rsc(html))
        val episodes = PageJson.arrays(PageJson.rsc(html), "episodes").single()
        assertEquals(1, episodes.length())
        assertEquals("A {title}", episodes.getJSONObject(0).getString("title"))
    }

    @Test fun incompleteOrNonJsonDataIsRejected() {
        assertNull(PageJson.container("{\"x\":[1,2]", 0))
        assertTrue(PageJson.arrays("{\"episodes\":null}", "episodes").isEmpty())
    }
}
