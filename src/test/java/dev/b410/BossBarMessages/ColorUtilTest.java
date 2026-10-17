package dev.b410.BossBarMessages;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ColorUtilTest {

    @Test
    void translatesLegacyCodes() {
        assertEquals("§aHello §lworld§r", ColorUtil.translate("&aHello &lworld&r"));
    }

    @Test
    void translatesHexColors() {
        assertEquals("§x§f§b§7§8§0§0B", ColorUtil.translate("&#FB7800B"));
    }

    @Test
    void mixesHexAndLegacy() {
        assertEquals("§x§0§0§f§f§0§0A§lB", ColorUtil.translate("&#00ff00A&lB"));
    }

    @Test
    void leavesInvalidHexAndPlainTextAlone() {
        assertEquals("&#GGGGGG plain", ColorUtil.translate("&#GGGGGG plain"));
        assertEquals("100% & more", ColorUtil.translate("100% & more"));
    }

    @Test
    void handlesNullAndRegexSpecialChars() {
        assertNull(ColorUtil.translate(null));
        assertEquals("$1 \\ §c$", ColorUtil.translate("$1 \\ &c$"));
    }
}
