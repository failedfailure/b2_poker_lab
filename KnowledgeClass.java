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

        if (roundCounter <= DEFENSIVE_ROUNDS_LIMIT) {
            b = 1;
        } else if (roundCounter % 30 == 0) {
            b = random.nextInt(Math.min(current.my_money, current.opponent_money)) + 1;
            b = Math.min(b, MAX_BID_LIMIT);
        } else {
            // current.my_card は int なので getStandardRankValue を使用
            int myCardStandardRank = getStandardRankValue(current.my_card);
            int predictedOpponentCardStandardRank = predictOpponentCard(); // predictOpponentCardもintランクを返す

            final int MAX_BID_BASE_TARGET = MAX_BID_LIMIT - 1;

            // 自分のカードの標準ランクが有効な範囲内か確認
            // mapInfoCardToIntRank(2) は InfoClassの2のカード(int 2)を内部ランク2にする
            // mapInfoCardToIntRank(1) は InfoClassの1のカード(int 1)を内部ランク14にする
            if (myCardStandardRank < getStandardRankValue(2) || myCardStandardRank > getStandardRankValue(1)) { // 2より小さい(不明な0以外) または A(1)より大きい (不正)
                b = 1; // 無効なカードの場合は最小ビッドに設定
            } else {
                // 内部ランクの最小値は2 (カードの2), 最大値は14 (カードのA)
                b = 1 + (int) Math.round(((double)(myCardStandardRank - getStandardRankValue(2)) / (getStandardRankValue(1) - getStandardRankValue(2))) * (MAX_BID_BASE_TARGET - 1));
                b = Math.max(1, b); // 少なくとも1であることを保証
            }

            // 相手の予測カードや状況に応じて微調整
            // 予測ランクが有効で、かつ相手のカードが非常に弱い場合のみ+1
            // getStandardRankValue(4) は InfoClassの4のカード(int 4)を内部ランク4にする
            if (predictedOpponentCardStandardRank > 0 && predictedOpponentCardStandardRank <= getStandardRankValue(4)) { // InfoClassの4(Card 4)
                b = Math.min(b + 1, MAX_BID_LIMIT);
            }

            if (current.my_money < b) {
                b = current.my_money;
            }
            if (current.opponent_money < b) {
                b = current.opponent_money;
            }

            b = Math.min(b, MAX_BID_LIMIT);
        }

        if (b > current.opponent_money) b = current.opponent_money;
        if (b > current.my_money) b = current.my_money;
        if (b < 1) b = 1;

        bid = "" + b;
        return bid;
    }

    public String decision() {
        decision = "n";

        // current.my_card は int なので getStandardRankValue を使用
        int myCardStandardRank = getStandardRankValue(current.my_card);

        if (roundCounter % 30 == 0) {
            return random.nextBoolean() ? "c" : "d";
        }

        if (roundCounter <= DEFENSIVE_ROUNDS_LIMIT) {
            // 自分のカードが'A'以上の標準ランク（つまり'A'）の場合のみコールを検討
            // getStandardRankValue(1) は InfoClassの1のカード(int 1)を内部ランク14にする
            if (myCardStandardRank >= getStandardRankValue(1)) { // 'A'以上の内部ランク
                decision = "c";
            } else {
                decision = "d";
            }
        }
        else {
            int predictedOpponentCardStandardRank = predictOpponentCard(); // predictOpponentCardもintランクを返す
            boolean isBluffing = detectBluffPattern();

            decision = "c";

            // 相手の予測ランクと自分のランクを比較。compareCardsは実際のカード値で比較するが、
            // ここでは予測なのでランク値の比較が適切
            if ((predictedOpponentCardStandardRank > myCardStandardRank && !isBluffing) ||
                // 自分のカードが非常に弱く（getStandardRankValue(3)以下、つまりInfoClassの3以下）、相手のビッドが自己資金の1/3を超える場合
                (myCardStandardRank <= getStandardRankValue(3) && current.opponent_bid > current.my_money / 3)) { // InfoClassの3 (Card 3)
                decision = "d";
            }

            if (isBluffing) {
                if (myCardStandardRank <= getStandardRankValue(4) && current.opponent_bid > current.my_money / 2) { // InfoClassの4 (Card 4)
                    decision = "d";
                } else {
                    decision = "c";
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

    private void HistoryUpdate() {
        for (int i = history.length - 2; i >= 0; i--) {
            history[i + 1] = CopyInfo(history[i]);
        }
        history[0] = CopyInfo(previous);
    }

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