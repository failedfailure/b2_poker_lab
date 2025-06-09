import java.util.Random;

public class KnowledgeClass {

    InfoClass current = new InfoClass();   // 現在のゲーム情報
    InfoClass previous = new InfoClass();  // 直前のゲーム情報

    InfoClass[] history = new InfoClass[10];  // 過去10回分の履歴

    String decision;
    String bid;

    Random rand = new Random();

    KnowledgeClass() {
        for (int i = 0; i < history.length; i++) {
            history[i] = new InfoClass();
        }
    }

    // ビッド額を1～5の間でランダムに決定（ただし残金以下）
    public String bid() {
        HistoryUpdate();

        int minBid = 1;
        int maxBid = 5;

        // 実際に賭けられる最大額（相手と自分の残金の下限も考慮）
        int actualMaxBid = Math.min(maxBid, Math.min(current.my_money, current.opponent_money));

        // 最小でも1を確保
        if (actualMaxBid < minBid) {
            bid = "1";
            return bid;
        }

        // minBid から actualMaxBid の範囲でランダムに選択
        int b = rand.nextInt(actualMaxBid - minBid + 1) + minBid;
        bid = "" + b;

        return bid;
    }

    // ランダムに「c（コール）」か「d（ドロップ）」を決定
    public String decision() {
        decision = rand.nextBoolean() ? "c" : "d";
        return decision;
    }

    // historyに直前のゲーム情報 previous を格納する
    private void HistoryUpdate() {
        for (int i = history.length - 2; i >= 0; i--) {
            history[i + 1] = CopyInfo(history[i]);
        }
        history[0] = CopyInfo(previous);
    }

    // InfoClassのインスタンスをコピーする
    private InfoClass CopyInfo(InfoClass Info) {
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
}

