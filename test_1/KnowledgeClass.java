import java.lang.Math;
import java.util.Random;

public class KnowledgeClass {

    InfoClass current = new InfoClass();
    InfoClass previous = new InfoClass();
    InfoClass[] history = new InfoClass[10];

    String decision;
    String bid;

    private int roundCounter = 0; // ゲームラウンドカウンタ
    private final Random random = new Random(); // 乱数生成器

    private final int DEFENSIVE_ROUNDS_LIMIT = 20; // 守備的に振る舞うラウンド数
    private final int MAX_BID_LIMIT = 5; // ビッド額の最大値

    KnowledgeClass() {
        for (int i = 0; i < history.length; i++) {
            history[i] = new InfoClass();
        }
    }

    public String bid() {
        roundCounter++; // ラウンド数を更新
        HistoryUpdate();

        int b = 0;
        bid = "";

        // 戦略選択ロジック
        if (roundCounter <= DEFENSIVE_ROUNDS_LIMIT) {
            // フェーズ1: 極めて保守的な戦略 (常に最小ビッド1)
            b = 1;
        } else if (roundCounter % 30 == 0) {
            // フェーズ3: 30ラウンドごとのランダム干渉 (ビッド額も最大5に制限)
            b = random.nextInt(Math.min(current.my_money, current.opponent_money)) + 1;
            b = Math.min(b, MAX_BID_LIMIT); // ランダムビッドも最大値制限に従う
        } else {
            // フェーズ2: 学習反演戦略
            int myCardValue = Character.getNumericValue(current.my_card);
            int predictedOpponentCard = predictOpponentCard();

            // **変更点:** 自分のカードの強さに応じて、ビッド額を1からMAX_BID_LIMITの範囲でマッピング
            // myCardValue (1-9) を MAX_BID_LIMIT (1-5) に線形マッピング
            // 例: カード1 -> ビッド1, カード9 -> ビッド5
            b = 1 + (int) Math.round(((double)(myCardValue - 1) / (9 - 1)) * (MAX_BID_LIMIT - 1));
            b = Math.max(1, b); // 少なくとも1であることを保証

            // 相手の予測カードや状況に応じて微調整
            // 相手のカードが弱いと予測される場合、少し強気に（ただしMAX_BID_LIMITを超えない）
            if (predictedOpponentCard < 5) {
                b = Math.min(b + 1, MAX_BID_LIMIT);
            }
            // 自分の残金が少ない場合、ビッドを抑える
            // これは最終チェックでも行われるが、計算過程で考慮することでより安全に
            if (current.my_money < b) {
                b = current.my_money;
            }
            // 相手の残金が少ない場合、ビッドを相手の残金に合わせる
            if (current.opponent_money < b) {
                b = current.opponent_money;
            }

            // 最終的にMAX_BID_LIMITを超えないことを保証
            b = Math.min(b, MAX_BID_LIMIT);
        }

        // 最終的なビッド額のチェック (残金を超えない、最低1を保証)
        if (b > current.opponent_money) b = current.opponent_money;
        if (b > current.my_money) b = current.my_money;
        if (b < 1) b = 1; // 最低ビッド額保証

        bid = "" + b;
        return bid;
    }

    public String decision() {
        decision = "n"; // 初期化

        int myCardValue = Character.getNumericValue(current.my_card);

        // 30ラウンドごとのランダム干渉
        if (roundCounter % 30 == 0) {
            return random.nextBoolean() ? "c" : "d";
        }

        // フェーズ1: 極めて保守的な戦略 (生存優先)
        if (roundCounter <= DEFENSIVE_ROUNDS_LIMIT) {
            // 自分のカードが8または9の場合のみコールを検討
            // それ以外は常にドロップしてリスクを最小化
            if (myCardValue >= 8) {
                decision = "c"; // 強いカードならコール
            } else {
                decision = "d"; // それ以外はドロップ
            }
        }
        // フェーズ2: 学習反演戦略 (20ラウンド以降)
        else {
            int predictedOpponentCard = predictOpponentCard();

            // 相手の行動パターン分析
            boolean isBluffing = detectBluffPattern(); // ブラフ検出

            // デフォルトはコールとする
            decision = "c";

            // ドロップする条件：
            // 1. 相手の予測カードが自分のカードより強く、かつブラフではない場合
            // 2. 自分のカードが非常に弱く（例: 3以下）、相手のビッドが自己資金の1/3を超える場合（ブラフでなくてもリスクが高すぎる）
            if ((predictedOpponentCard > myCardValue && !isBluffing) ||
                (myCardValue <= 3 && current.opponent_bid > current.my_money / 3)) {
                decision = "d";
            }

            // ブラフが検出された場合、コールに強く傾倒する
            if (isBluffing) {
                // ただし、自分のカードが非常に弱く、相手のビッドが自己資金の半分を超えるような極端な状況では、
                // ブラフであってもドロップする可能性がある
                if (myCardValue <= 2 && current.opponent_bid > current.my_money / 2) {
                    decision = "d"; // 絶望的な状況ではドロップ
                } else {
                    decision = "c"; // それ以外の場合はブラフをコール
                }
            }
        }

        return decision;
    }

    // 相手のカード予測メソッド
    private int predictOpponentCard() {
        int sameBidCount = 0;
        int cardSum = 0;

        for (InfoClass h : history) {
            // 履歴のopponent_cardが有効な数字文字('1'〜'9')であり、かつビッド額が一致する場合のみ考慮
            if (h.opponent_card != '0' && h.opponent_bid == current.opponent_bid) {
                int historicalOpponentCard = Character.getNumericValue(h.opponent_card);
                if (historicalOpponentCard >= 1 && historicalOpponentCard <= 9) { // 実際のカード値の範囲チェック
                    sameBidCount++;
                    cardSum += historicalOpponentCard;
                }
            }
        }

        // データがない場合は中央値5を返す
        return sameBidCount > 0 ? cardSum / sameBidCount : 5;
    }

    // ブラフ検出メソッド
    private boolean detectBluffPattern() {
        int generalHighBidLowCardCount = 0; // 一般的なブラフパターン (高ビッド + 低カード)
        int maxBidSmallCardBluffCount = 0; // 特定のブラフパターン (ビッド5 + 非常に小さなカード)
        int totalAnalyzedCases = 0;

        for (InfoClass h : history) {
            // カード情報とビッド情報が有効な場合のみ分析
            if (h.opponent_card != '0' && h.opponent_bid > 0) {
                int historicalOpponentCard = Character.getNumericValue(h.opponent_card);
                if (historicalOpponentCard >= 1 && historicalOpponentCard <= 9) { // 有効なカード値のみ
                    // 1. 一般的なブラフ検出: 低カード(1-5)で比較的高いビッド(自己資金の1/3超)
                    if (historicalOpponentCard <= 5 && h.opponent_bid > h.opponent_money / 3) {
                        generalHighBidLowCardCount++;
                    }
                    // 2. 特定のブラフ検出: ビッド額が最大(5)かつカードが非常に小さい(1-3)
                    if (h.opponent_bid == MAX_BID_LIMIT && historicalOpponentCard <= 3) {
                        maxBidSmallCardBluffCount++;
                    }
                    totalAnalyzedCases++;
                }
            }
        }

        // 十分なデータがある場合にブラフを判定
        // - 一般的なブラフの傾向が強い (50%超)
        // - または、最大ビッドでの小さなカードのブラフが顕著 (33%超)
        boolean generalBluffLikely = totalAnalyaledCases > 3 && generalHighBidLowCardCount * 2 > totalAnalyzedCases;
        boolean maxBidBluffLikely = totalAnalyzedCases > 2 && maxBidSmallCardBluffCount * 3 > totalAnalyzedCases;

        return generalBluffLikely || maxBidBluffLikely;
    }

    // historyに直前のゲーム情報 previous を格納する
    private void HistoryUpdate() {
        // 履歴をシフト（最古の情報を破棄）
        for (int i = history.length - 2; i >= 0; i--) {
            history[i + 1] = CopyInfo(history[i]);
        }
        // 直前のゲーム情報を履歴の最新に追加
        history[0] = CopyInfo(previous);
    }

    // InfoClassのインスタンスをコピーする
    private InfoClass CopyInfo(InfoClass info) {
        InfoClass tmpInfo = new InfoClass();
        tmpInfo.my_bid = info.my_bid;
        tmpInfo.my_card = info.my_card;
        tmpInfo.my_decision = info.my_decision;
        tmpInfo.my_money = info.my_money;
        tmpInfo.opponent_bid = info.opponent_bid;
        tmpInfo.opponent_card = info.opponent_card;
        tmpInfo.opponent_decision = info.opponent_decision;
        tmpInfo.opponent_money = info.opponent_money;
        return tmpInfo;
    }
}