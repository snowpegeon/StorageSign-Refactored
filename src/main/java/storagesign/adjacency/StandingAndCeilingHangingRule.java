package storagesign.adjacency;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * 立て看板（通常）と天井吊り看板（HANGING_SIGN）を扱う。
 */
public final class StandingAndCeilingHangingRule implements SsAdjacencyRule {

    @Override
    public List<SsAdjacencyMatch> findMatches(SsAdjacencyQuery query) {
        List<SsAdjacencyMatch> matches = new ArrayList<>(2);
        Block container = query.containerBlock();

        // 通常立て看板: コンテナの上にある看板を対象。
        Block up = container.getRelative(BlockFace.UP);
        if (AdjacencyRuleSupport.isStandingSign(up.getType())) {
            SsAdjacencyMatch match = AdjacencyRuleSupport.toMatchIfStorageSign(up, query.item());
            if (match != null) matches.add(match);
        }

        // 天井吊り看板: コンテナの下に吊り下がる看板を対象。
        Block down = container.getRelative(BlockFace.DOWN);
        if (AdjacencyRuleSupport.isCeilingHangingSign(down.getType())) {
            SsAdjacencyMatch match = AdjacencyRuleSupport.toMatchIfStorageSign(down, query.item());
            if (match != null) matches.add(match);
        }

        return matches;
    }
}
