package wacky.storagesign.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.Test;

class PotionHelperTest {

    @Test
    void resolvesGenericNormalPotionWithoutMatchingLongOrStrongVariants() {
        assertEquals(PotionType.NIGHT_VISION, PotionHelper.fromSignText("NIGHT", "0"));
        assertEquals(PotionType.POISON, PotionHelper.fromSignText("POISO", "0"));
    }

    @Test
    void resolvesGenericExtendedAndStrongPotionVariantsByEnhanceCode() {
        assertEquals(PotionType.LONG_NIGHT_VISION, PotionHelper.fromSignText("NIGHT", "1"));
        assertEquals(PotionType.LONG_POISON, PotionHelper.fromSignText("POISO", "1"));
        assertEquals(PotionType.STRONG_POISON, PotionHelper.fromSignText("POISO", "2"));
    }

    @Test
    void preservesSpecialCasePotionMappings() {
        assertEquals(PotionType.HEALING, PotionHelper.fromSignText("HEAL", "0"));
        assertEquals(PotionType.STRONG_HEALING, PotionHelper.fromSignText("HEAL", "2"));
        assertEquals(PotionType.LONG_WATER_BREATHING, PotionHelper.fromSignText("BREAT", "1"));
    }
}
