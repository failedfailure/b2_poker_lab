import java.lang.Math;
import java.util.Random;

public class KnowledgeClass {

    InfoClass current = new InfoClass();  // 現在のゲーム情報
    InfoClass previous = new InfoClass(); // 直前のゲーム情報

    // 過去200回分のゲーム履歴情報
    InfoClass[] history = new InfoClass[200];

    String decision; // コール or ドロップの宣言用
    String bid;      // ビッド額宣言用

    // ランダムの確率（例えば10%の確率でランダムに最大ベット額5を選択）
    double randomThreshold = 0.40; // 40% に変更

    // リスク考慮のための追加パラメータ (持ち金を考慮しないため、MONEY_RISK_FACTORは実質使われない)
    private final double MY_CARD_RISK_FACTOR = 1.5;
    private final double OPPONENT_BID_RISK_FACTOR = 0.8;

    // Randomインスタンスはクラスのフィールドとして一度だけ初期化する
    private Random rand = new Random();

    // ゲームラウンド数を追跡する変数
    private int gameRound = 0;

    // コンストラクタ：履歴配列を初期化
    KnowledgeClass() {
        for (int i = 0; i < history.length; i++) {
            history[i] = new InfoClass();
        }
    }

    // ビッド数の決定
    public String bid() {
        // ゲーム履歴情報を更新
        HistoryUpdate();

        // ゲームラウンド数をインクリメント
        gameRound++;

        int b = 0;
        bid = ""; // 初期化

        // 最初の25回は必ず1をベットする (データ収集期間)
        if (gameRound <= 25) {
            b = 1;
            // 1ベットであっても、所持金が0の場合はベットできないので0にする
            if (current.my_money <= 0) {
                System.err.println("Warning: Not enough money for even minimum bid (1). Betting 0.");
                b = 0;
            }
        } else {
            // --- 26回目以降の通常のベットロジック ---

            // 自分のカードが極端に弱い場合（例: 1か2）は1をベットする
            if (current.my_card == 1 || current.my_card == 2) {
                b = 1;
            } 
            // 相手のカードが 2, 3, 4 のときのみランダムで最大ベット額（5）を選択
            else if (isRandomBettingAllowed(current.opponent_card) && rand.nextDouble() < randomThreshold) {
                b = 5; // ランダムで最大ベット額（5）を選択
            } else {
                // 通常のビッド額決定ロジック
                int baseBid = Math.min(
                        5, // 最大ベット額を仮に5とする。
                        (10/ (current.opponent_card + 1)) + 1);

                double riskAdjustedBid = baseBid;

                double myCardStrengthFactor = (double)current.my_card / 9.0;
                riskAdjustedBid *= myCardStrengthFactor * MY_CARD_RISK_FACTOR + (1 - MY_CARD_RISK_FACTOR);
                
                b = (int) Math.round(riskAdjustedBid);
            }

            // 物理的にベットできない場合は、所持金を超えることはできないため、この制限は残す
            b = Math.min(b, current.my_money);

            // 相手の所持金を超えるベットも無意味なため、相手の所持金も上限とする
            b = Math.min(b, current.opponent_money);

            // 最終的なベット額が0以下になった場合の処理
            if (b <= 0) {
                if (current.my_money > 0) {
                    b = 1; // 所持金がある場合は最低ベット額を1とする
                } else {
                    System.err.println("Warning: Not enough money to make any bid. Betting 0.");
                    b = 0; // 所持金がないのでベットできない
                }
            }
        } // --- 26回目以降の通常のベットロジック終了 ---

        bid = "" + b;
        return bid;
    }

    // コール or ドロップの決定ルール（予測強化、ランダム要素追加）
    public String decision() {
        decision = "n"; // 初期化

        double weightedSum = 0.0;
        double totalWeight = 0.0;

        // 履歴からビッド額が一致するものを集計し、重みづけ平均を計算
        for (int i = 0; i < history.length; i++) {
            if (history[i] != null && current.opponent_bid == history[i].opponent_bid) {
                // ★変更: 重みを二乗して、直近の情報をより重くする
                double weight = (double)(history.length - i) * (history.length - i); 
                weightedSum += history[i].my_card * weight;
                totalWeight += weight;
            }
        }

        int predictedOpponentCard = 9; // 予測できない場合は高めのカードと仮定

        // 重みづけ平均で予測（履歴がある場合）
        if (totalWeight > 0) {
            predictedOpponentCard = (int) Math.round(weightedSum / totalWeight);
        }

        boolean shouldDrop = false;

        // 1. 自分のカードが相手の予測よりも大幅に弱い場合
        if (current.my_card + 4 <= predictedOpponentCard) {
            shouldDrop = true;
        }

        // 3. 相手のビッド額が非常に高い場合
        if (current.opponent_bid >= 5) {
            if (current.my_card +3<= predictedOpponentCard) {
                shouldDrop = true;
            }
        }

        // 相手のカードが特定の値以下（例: 4以下）であれば、たとえshouldDropがtrueでもドロップしない
        if (current.opponent_card <= 4) { 
             shouldDrop = false;
        }

        // 最終的な判断ロジック
        if (shouldDrop) {
            decision = "d"; // ドロップ
        } else if (current.opponent_card > current.my_card) {
            decision = "d"; // ドロップ
        } else {
            decision = "c"; // コール
        }

        return decision;
    }

    // historyに直前のゲーム情報 previous を格納する
    private void HistoryUpdate() {
        for (int i = history.length - 1; i > 0; i--) {
            history[i] = CopyInfo(history[i - 1]);
        }
        history[0] = CopyInfo(previous);
    }

    // InfoClassのインスタンスをコピーする
    private InfoClass CopyInfo(InfoClass Info) {
        if (Info == null) {
            return new InfoClass();
        }
        InfoClass tmpInfo = new InfoClass();
        tmpInfo.my_bid = Info.my_bid;
        tmpInfo.my_card = Info.my_card;
        tmpInfo.my_decision = Info.my_decision;
        tmpInfo.my_money = Info.my_money;
        tmpInfo.opponent_bid = Info.opponent_bid;
        tmpInfo.opponent_card = Info.opponent_card;
        tmpInfo.opponent_decision = Info.opponent_decision;
        tmpInfo.opponent_money = Info.opponent_money;
        return tmpInfo;
    }

    // 相手カードが2, 3, 4 のときにランダムベットを許可するかどうかをチェック
    private boolean isRandomBettingAllowed(int opponentCard) {
        return opponentCard == 2 || opponentCard == 3 || opponentCard == 4;
    }
}
