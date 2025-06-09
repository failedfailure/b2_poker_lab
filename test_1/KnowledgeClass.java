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
     * カードの文字表現を標準的なポーカーの順位に基づいた数値に変換する。
     * A > K > Q > J > T > 9 > 8 > 7 > 6 > 5 > 4 > 3 > 2 の順で、Aが最も高い。
     * これはカードの一般的な"強さ"を評価するために使用される。
     * @param card 変換するカード文字 (例: 'A', 'K', '2', '7')
     * @return 標準順位に基づくカードの強さを示す数値 (A=14, K=13, ..., 2=2)
     */
    private int getStandardRankValue(char card) {
        switch (card) {
            case 'A': return 14; // 最も強い（通常時）
            case 'K': return 13;
            case 'Q': return 12;
            case 'J': return 11;
            case 'T': return 10; // '10' は 'T' で表されると仮定
            case '9': return 9;
            case '8': return 8;
            case '7': return 7;
            case '6': return 6;
            case '5': return 5;
            case '4': return 4;
            case '3': return 3;
            case '2': return 2; // 最も弱い（通常時）
            default: return 0; // 不明なカードや初期値 ('0')
        }
    }

    /**
     * 二枚のカードをゲームの特殊な勝利ルールに基づいて比較する。
     * ルール: A > K > Q > J > T > 9 > 8 > 7 > 6 > 5 > 4 > 3 > 2 が基本だが、
     * 特殊な条件として「2はAにのみ勝つ」。
     * @param card1 比較する最初のカード
     * @param card2 比較する2番目のカード
     * @return card1が勝つ場合は正の値、card2が勝つ場合は負の値、引き分けの場合は0 (通常は発生しない)
     */
    private int compareCards(char card1, char card2) {
        // 特殊ルール: '2' は 'A' にのみ勝つ
        if (card1 == '2' && card2 == 'A') {
            return 1; // card1 ('2') が card2 ('A') に勝つ
        }
        if (card1 == 'A' && card2 == '2') {
            return -1; // card2 ('2') が card1 ('A') に勝つ
        }

        // それ以外の全てのケースでは標準の順位で比較
        int rank1 = getStandardRankValue(card1);
        int rank2 = getStandardRankValue(card2);

        return rank1 - rank2; // rank1がrank2より大きければ正、小さければ負、等しければ0
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
            int myCardStandardRank = getStandardRankValue(current.my_card);
            int predictedOpponentCardStandardRank = predictOpponentCard();

            // **変更点:** 自分のカードの強さに応じて、ビッド額を1からMAX_BID_LIMIT-1の範囲でマッピング
            // 最高ランクのカード（'A'）でも、ベースビッドはMAX_BID_LIMIT-1 (4) になるようにする
            final int MAX_BID_BASE_TARGET = MAX_BID_LIMIT - 1; // ベースビッドの最大ターゲット値 (例: 4)

            b = 1 + (int) Math.round(((double)(myCardStandardRank - getStandardRankValue('2')) / (getStandardRankValue('A') - getStandardRankValue('2'))) * (MAX_BID_BASE_TARGET - 1));
            b = Math.max(1, b); // 少なくとも1であることを保証

            // 相手の予測カードや状況に応じて微調整
            // **変更点:** 相手のカードが非常に弱い（標準ランクで'4'以下）と予測される場合のみ、ベースビッドに+1する
            // これで初めてMAX_BID_LIMIT (5) に達する可能性がある
            if (predictedOpponentCardStandardRank <= getStandardRankValue('4')) {
                b = Math.min(b + 1, MAX_BID_LIMIT); // MAX_BID_LIMITを超えないように
            }
            // 自分の残金が少ない場合、ビッドを抑える
            if (current.my_money < b) {
                b = current.my_money;
            }
            // 相手の残金が少ない場合、ビッドを相手の残金に合わせる
            if (current.opponent_money < b) {
                b = current.opponent_money;
            }

            // 最終的にMAX_BID_LIMITを超えないことを保証 (二重チェックだが安全のため残す)
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

        // 30ラウンドごとのランダム干渉
        if (roundCounter % 30 == 0) {
            return random.nextBoolean() ? "c" : "d";
        }

        // フェーズ1: 極めて保守的な戦略 (生存優先)
        if (roundCounter <= DEFENSIVE_ROUNDS_LIMIT) {
            // 自分のカードが'A'以上の標準ランク（つまり'A'）の場合のみコールを検討
            if (myCardStandardRank >= getStandardRankValue('A')) {
                decision = "c"; // 強いカードならコール
            } else {
                decision = "d"; // それ以外はドロップ
            }
        }
        // フェーズ2: 学習反演戦略 (20ラウンド以降)
        else {
            int predictedOpponentCardStandardRank = predictOpponentCard();

            // 相手の行動パターン分析
            boolean isBluffing = detectBluffPattern(); // ブラフ検出

            // デフォルトはコールとする
            decision = "c";

            // ドロップする条件：
            // 1. 相手の予測カード（標準ランク）が自分のカード（標準ランク）より強く、かつブラフではない場合
            //    注意: predictOpponentCardはcharではなくintランクを返すため、compareCardsは直接適用できない
            //    ここでは予測ランクに基づいて一般的な優劣を判断する
            // 2. 自分のカードが非常に弱く（例: '3'以下）、相手のビッドが自己資金の1/3を超える場合（ブラフでなくてもリスクが高すぎる）
            if ((predictedOpponentCardStandardRank > myCardStandardRank && !isBluffing) ||
                (myCardStandardRank <= getStandardRankValue('3') && current.opponent_bid > current.my_money / 3)) {
                decision = "d";
            }

            // ブラフが検出された場合、コールに強く傾倒する
            if (isBluffing) {
                // ただし、自分のカードが非常に弱く（例: '4'以下）、相手のビッドが自己資金の半分を超えるような極端な状況では、
                // ブラフであってもドロップする可能性がある
                if (myCardStandardRank <= getStandardRankValue('4') && current.opponent_bid > current.my_money / 2) {
                    decision = "d"; // 絶望的な状況ではドロップ
                } else {
                    decision = "c"; // それ以外の場合はブラフをコール
                }
            }
        }

        return decision;
    }

    // 相手のカード予測メソッド
    // このメソッドは、相手のカードの"標準的な強さ"を予測する
    private int predictOpponentCard() {
        int sameBidCount = 0;
        int cardStrengthSum = 0;

        for (InfoClass h : history) {
            // 履歴のopponent_cardが有効なカード文字であり、かつビッド額が一致する場合のみ考慮
            if (h.opponent_card != '0' && h.opponent_bid > 0) { // Also ensure bid was valid
                int historicalOpponentCardStandardRank = getStandardRankValue(h.opponent_card);
                if (historicalOpponentCardStandardRank > 0) { // getStandardRankValueが0を返す場合は不正なカード
                    sameBidCount++;
                    cardStrengthSum += historicalOpponentCardStandardRank;
                }
            }
        }

        // データがない場合は中央値に近い強さ（例: '7'の標準ランク）を返す
        return sameBidCount > 0 ? cardStrengthSum / sameBidCount : getStandardRankValue('7');
    }

    // ブラフ検出メソッド
    // このメソッドも、相手のカードの"標準的な強さ"に基づいてブラフパターンを検出する
    private boolean detectBluffPattern() {
        int generalHighBidLowCardCount = 0; // 一般的なブラフパターン (高ビッド + 低カード)
        int maxBidSmallCardBluffCount = 0; // 特定のブラフパターン (ビッド5 + 非常に小さなカード)
        int totalAnalyzedCases = 0;

        for (InfoClass h : history) {
            // カード情報とビッド情報が有効な場合のみ分析
            if (h.opponent_card != '0' && h.opponent_bid > 0) {
                int historicalOpponentCardStandardRank = getStandardRankValue(h.opponent_card);
                if (historicalOpponentCardStandardRank > 0) { // 有効なカード強さのみ
                    // 1. 一般的なブラフ検出: 低カード（標準ランクで'5'以下）で比較的高いビッド(自己資金の1/3超)
                    if (historicalOpponentCardStandardRank <= getStandardRankValue('5') && h.opponent_bid > h.opponent_money / 3) {
                        generalHighBidLowCardCount++;
                    }
                    // 2. 特定のブラフ検出: ビッド額が最大(5)かつカードが非常に小さい（標準ランクで'4'以下）
                    if (h.opponent_bid == MAX_BID_LIMIT && historicalOpponentCardStandardRank <= getStandardRankValue('4')) {
                        maxBidSmallCardBluffCount++;
                    }
                    totalAnalyzedCases++;
                }
            }
        }

        // 十分なデータがある場合にブラフを判定
        // - 一般的なブラフの傾向が強い (50%超)
        // - または、最大ビッドでの小さなカードのブラフが顕著 (33%超)
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