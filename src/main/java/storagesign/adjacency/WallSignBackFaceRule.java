package storagesign.adjacency;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;

/**
 * 通常の壁看板（*_WALL_SIGN）を扱う。
 */
public final class WallSignBackFaceRule implements SsAdjacencyRule {

    private static final BlockFace[] HORIZONTAL = {
        BlockFace.SOUTH, BlockFace.NORTH, BlockFace.EAST, BlockFace.WEST
    };

    @Override
    public Optional<SsAdjacencyMatch> findFirstMatch(SsAdjacencyQuery query) {
        Block container = query.containerBlock();

        for (BlockFace face : HORIZONTAL) {
            Block adjacent = container.getRelative(face);
            if (!AdjacencyRuleSupport.isWallStandingSign(adjacent.getType())) continue;
            if (!(adjacent.getBlockData() instanceof Directional directional)) continue;
            if (directional.getFacing() != face) continue;

            SsAdjacencyMatch match = AdjacencyRuleSupport.toMatchIfStorageSign(adjacent, query.item());
            if (match != null) return Optional.of(match);
        }
        return Optional.empty();
    }

    @Override
    public List<SsAdjacencyMatch> findMatches(SsAdjacencyQuery query) {
        List<SsAdjacencyMatch> matches = new ArrayList<>(1);
        Block container = query.containerBlock();

        for (BlockFace face : HORIZONTAL) {
            Block adjacent = container.getRelative(face);
            if (!AdjacencyRuleSupport.isWallStandingSign(adjacent.getType())) continue;
            if (!(adjacent.getBlockData() instanceof Directional directional)) continue;
            if (directional.getFacing() != face) continue;

            SsAdjacencyMatch match = AdjacencyRuleSupport.toMatchIfStorageSign(adjacent, query.item());
            if (match != null) matches.add(match);
        }

        return matches;
    }
}
