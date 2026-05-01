package storagesign.adjacency;

import java.util.List;
import java.util.Optional;

/**
 * 1 種類の看板配置ルールに基づいて隣接 SS を検出する戦略インターフェース。
 */
public interface SsAdjacencyRule {

    /**
     * ルール順で最初に一致した候補を返す。
     * 高頻度パス（イベント処理）では余分なリスト生成を避けるためこちらを使う。
     */
    Optional<SsAdjacencyMatch> findFirstMatch(SsAdjacencyQuery query);

    /**
     * ルールに一致する候補を返す。
     */
    List<SsAdjacencyMatch> findMatches(SsAdjacencyQuery query);
}
