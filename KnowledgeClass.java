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

    // 新しいパラメータ: AIの攻撃性レベル (1:非常に保守的 ～ 5:非常に攻撃的)
    private int aggressionLevel; // 1 to 5

    // デフォルトコンストラクタ (中程度の攻撃性)
    KnowledgeClass() {
        this(3); // デフォルトは中程度の攻撃性レベル3
    }

    // 攻撃性レベルを設定するコンストラクタ
    public KnowledgeClass(int aggressionLevel) {
        if (aggressionLevel < 1) this.aggressionLevel = 1;
        else if (aggressionLevel > 5) this.aggressionLevel = 5;
        else this.aggressionLevel = aggressionLevel;

        for (int i = 0; i < history.length; i++) {
            history[i] = new InfoClass();
        }
    }

    /**
     * InfoClassから受け取るint型のカード値（例: 1=A, 2=2...13=K）を、
     * ゲームの勝利ルールに基づいた内部的な標準ポーカー順位（A=14, K=13...2=2）に変換する。
     * InfoClassのカード値の仮定: 1=Ace, 2-10=数字カード, 11=Jack, 12=Queen, 13=King, 0=不明。
     *
     * @param infoCardValue InfoClassからのint型カード値
     * @return 内部的な標準順位を示す数値 (A=14, K=13, ..., 2=2)。不明なカードや不正な値は0を返す。
     */
    private int getStandardRankValue(int infoCardValue) {
        if (infoCardValue == 1) { // InfoClassのAce (1) を内部ランクの14にマッピング
            return 14;
        }
        if (infoCardValue >= 2 && infoCardValue <= 13) { // 2からKing(13)はそのまま内部ランクとして使用
            return infoCardValue;
        }
        return 0; // 不明 (0) または範囲外の値
    }

    /**
     * 二枚のint型カードをゲームの特殊な勝利ルールに基づいて比較する。
     * ルール: A > K > Q > J > 10 > 9 > 8 > 7 > 6 > 5 > 4 > 3 > 2 が基本だが、
     * 特殊な条件として「2はAにのみ勝つ」。
     *
     * @param infoCardValue1 比較する最初のカード (InfoClassからのint型値)
     * @param infoCardValue2 比較する2番目のカード (InfoClassからのint型値)
     * @return infoCardValue1が勝つ場合は正の値、infoCardValue2が勝つ場合は負の値、引き分けの場合は0。
     */
    private int compareCards(int infoCardValue1, int infoCardValue2) {
        // 特殊ルール: InfoClassの2 (Deuce) は InfoClassの1 (Ace) にのみ勝つ
        if (infoCardValue1 == 2 && infoCardValue2 == 1) {
            return 1; // card1 (Deuce) が card2 (Ace) に勝つ
        }
        if (infoCardValue1 == 1 && infoCardValue2 == 2) {
            return -1; // card2 (Deuce) が card1 (Ace) に勝つ
        }

        // それ以外の全てのケースでは標準の順位で比較
        int rank1 = getStandardRankValue(infoCardValue1);
        int rank2 = getStandardRankValue(infoCardValue2);

        // 不明なカード値の場合は比較できない
        if (rank1 == 0 && rank2 == 0) return 0; // 両方不明
        if (rank1 == 0) return -1; // card1不明、card2が実質勝ち
        if (rank2 == 0) return 1;  // card2不明、card1が実質勝ち

        return rank1 - rank2;
    }


    public String bid() {
        roundCounter++;
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
            int myCardStandardRank = getStandardRankValue(current.my_card);
            int predictedOpponentCardStandardRank = predictOpponentCard();

            // 自分のカードの強さに応じて、ビッド額を1からMAX_BID_LIMITの範囲でマッピング
            if (myCardStandardRank < getStandardRankValue(2)) { // 例えば InfoClassの0 (不明) の場合
                b = 1; // 無効なカードの場合は最小ビッドに設定
            } else {
                // 改良されたスケーリング: 2(Deuce)から14(Ace)の内部ランクを1からMAX_BID_LIMITまで均等にマッピング
                // 攻撃性レベルに応じて、ビッドの傾きを調整
                double bidScaleFactor = (MAX_BID_LIMIT - 1) * (1.0 + (aggressionLevel - 3) * 0.1); // 中立(3)で1.0倍, 低(1)で0.8倍, 高(5)で1.2倍
                b = 1 + (int) Math.round(((double)(myCardStandardRank - getStandardRankValue(2)) / (getStandardRankValue(1) - getStandardRankValue(2))) * bidScaleFactor);
                b = Math.max(1, b); // 少なくとも1であることを保証
            }

            // 相手の予測カードや状況に応じて微調整
            if (predictedOpponentCardStandardRank > 0) {
                if (predictedOpponentCardStandardRank < myCardStandardRank) {
                    // 相手のカードが自分より弱いと予測される場合: 強気にビッドを上げる
                    // 攻撃性が高いほど、より積極的に上げる
                    int aggressionModifier = (aggressionLevel - 1); // 0 (保守的) から 4 (攻撃的)
                    b = Math.min(b + (myCardStandardRank - predictedOpponentCardStandardRank) / Math.max(1, (4 - aggressionModifier)), MAX_BID_LIMIT);
                } else if (predictedOpponentCardStandardRank >= myCardStandardRank) {
                    // 相手のカードが自分と同等か強いと予測される場合:
                    // 自分のカードが弱めだが、相手がブラフしている可能性がある場合に、ブラフでビッドを上げるチャンス
                    double bluffChance = 0.3 + (aggressionLevel - 1) * 0.05; // 攻撃性が高いほどブラフ率UP (0.3 -> 0.5)
                    if (myCardStandardRank <= getStandardRankValue(5) && detectBluffPattern() && random.nextDouble() < bluffChance) {
                         b = Math.min(b + random.nextInt(aggressionLevel) + 1, MAX_BID_LIMIT); // 攻撃性レベルが高いほど、より大きくブラフビッド
                    } else {
                        // それ以外は少し保守的になる
                        int conservativeReduction = random.nextInt(2); // 0-1だけ下げる
                        if (aggressionLevel <= 2) conservativeReduction = random.nextInt(3); // 保守的ならもっと下げる
                        b = Math.max(1, b - conservativeReduction);
                    }
                }
            }

            // 自分の残金が少ない場合、ビッドを抑える
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

        int myCardStandardRank = getStandardRankValue(current.my_card);

        // 30ラウンドごとのランダム干渉 (攻撃性レベルの影響を受けない)
        if (roundCounter % 30 == 0) {
            return random.nextBoolean() ? "c" : "d";
        }

        // フェーズ1: 極めて保守的な戦略 (生存優先)
        if (roundCounter <= DEFENSIVE_ROUNDS_LIMIT) {
            if (myCardStandardRank >= getStandardRankValue(1)) { // InfoClassの1 (Ace)
                decision = "c"; // 強いカードならコール
            } else {
                decision = "d"; // それ以外はドロップ
            }
        }
        // フェーズ2: 学習反演戦略 (20ラウンド以降)
        else {
            int predictedOpponentCardStandardRank = predictOpponentCard();
            boolean isBluffing = detectBluffPattern(); // ブラフ検出

            // デフォルトはコールとする
            decision = "c";

            // ドロップする条件：
            // 1. 相手の予測カード（標準ランク）が自分のカード（標準ランク）より強く、かつブラフではない場合
            double dropChanceStrongerOpponent = 0.85 - (aggressionLevel - 1) * 0.1; // 攻撃性が高いほどドロップ率DOWN (0.85 -> 0.45)
            if (predictedOpponentCardStandardRank > myCardStandardRank && !isBluffing) {
                if (random.nextDouble() < dropChanceStrongerOpponent) {
                    decision = "d";
                } else {
                    decision = "c";
                }
            }
            // 2. 自分のカードが非常に弱く（例: Card 3以下）、相手のビッドが自己資金の1/3を超える場合
            double dropChanceWeakCardHighBid = 0.7 - (aggressionLevel - 1) * 0.1; // 攻撃性が高いほどドロップ率DOWN (0.7 -> 0.3)
            if (myCardStandardRank <= getStandardRankValue(3) && current.opponent_bid > current.my_money / 3) { // InfoClassの3 (Card 3)
                if (random.nextDouble() < dropChanceWeakCardHighBid) {
                    decision = "d";
                } else {
                    decision = "c";
                }
            }

            // ブラフが検出された場合、コールに強く傾倒する
            if (isBluffing) {
                // ただし、自分のカードが非常に弱く（例: Card 4以下）、相手のビッドが自己資金の半分を超えるような極端な状況では、
                // ブラフであってもドロップする可能性も残す
                double dropChanceDesperateBluff = 0.6 - (aggressionLevel - 1) * 0.1; // 攻撃性が高いほどドロップ率DOWN (0.6 -> 0.2)
                if (myCardStandardRank <= getStandardRankValue(4) && current.opponent_bid > current.my_money / 2) { // InfoClassの4 (Card 4)
                    if (random.nextDouble() < dropChanceDesperateBluff) {
                        decision = "d";
                    } else {
                        decision = "c";
                    }
                } else {
                    decision = "c"; // それ以外の場合はブラフをほぼ確実にコール
                }
            }
        }

        return decision;
    }

    /**
     * 相手のカードの標準的な強さを予測するメソッド。
     * 履歴の opponent_card は int 型なので getStandardRankValue を使用する。
     * @return 予測される相手のカードの内部標準ランク (int)。
     */
    private int predictOpponentCard() {
        int sameBidCount = 0;
        int cardStrengthSum = 0;

        for (InfoClass h : history) {
            // h.opponent_card は int 型なので getStandardRankValue を使用
            if (h.opponent_card != 0 && h.opponent_bid > 0) { // 0 は不明なカードのint値
                int historicalOpponentCardStandardRank = getStandardRankValue(h.opponent_card);
                if (historicalOpponentCardStandardRank > 0) { // 0 は不正なランク
                    sameBidCount++;
                    cardStrengthSum += historicalOpponentCardStandardRank;
                }
            }
        }

        // データがない場合は中央値に近い強さ（例: InfoClassの7 -> 内部ランクの7）を返す
        return sameBidCount > 0 ? cardStrengthSum / sameBidCount : getStandardRankValue(7);
    }

    /**
     * 相手のブラフパターンを検出するメソッド。
     * 履歴の opponent_card は int 型なので getStandardRankValue を使用する。
     * @return ブラフの可能性が高い場合は true、そうでない場合は false。
     */
    private boolean detectBluffPattern() {
        int generalHighBidLowCardCount = 0;
        int maxBidSmallCardBluffCount = 0;
        int totalAnalyzedCases = 0;

        for (InfoClass h : history) {
            // h.opponent_card は int 型なので getStandardRankValue を使用
            if (h.opponent_card != 0 && h.opponent_bid > 0) { // 0 は不明なカードのint値
                int historicalOpponentCardStandardRank = getStandardRankValue(h.opponent_card);
                if (historicalOpponentCardStandardRank > 0) { // 0 は不正なランク
                    // 1. 一般的なブラフ検出: 低カード（内部ランクで5以下）で比較的高いビッド
                    if (historicalOpponentCardStandardRank <= getStandardRankValue(5) && h.opponent_bid > h.opponent_money / 3) { // InfoClassの5 (Card 5)
                        generalHighBidLowCardCount++;
                    }
                    // 2. 特定のブラフ検出: ビッド額が最大(5)かつカードが非常に小さい（内部ランクで4以下）
                    if (h.opponent_bid == MAX_BID_LIMIT && historicalOpponentCardStandardRank <= getStandardRankValue(4)) { // InfoClassの4 (Card 4)
                        maxBidSmallCardBluffCount++;
                    }
                    totalAnalyzedCases++;
                }
            }
        }

        boolean generalBluffLikely = totalAnalyzedCases > 3 && generalHighBidLowCardCount * 2 > totalAnalyzedCases;
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
        tmpInfo.my_card = info.my_card; // int to int (this is fine)
        tmpInfo.my_decision = info.my_decision;
        tmpInfo.my_money = info.my_money;
        tmpInfo.opponent_bid = info.opponent_bid;
        tmpInfo.opponent_card = info.opponent_card; // int to int (this is fine)
        tmpInfo.opponent_decision = info.opponent_decision;
        tmpInfo.opponent_money = info.opponent_money;
        return tmpInfo;
    }
}