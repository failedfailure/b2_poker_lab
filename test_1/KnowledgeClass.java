import java.lang.Math;

public class KnowledgeClass {

    InfoClass current = new InfoClass(); // ゲーム情報
    InfoClass previous = new InfoClass(); // 直前のゲーム情報

    // 過去10回分のゲーム履歴情報
    // n回前のゲーム情報をhistory[n-1]に記憶
    InfoClass[] history = new InfoClass[10];

    String decision; // コールorドロップの宣言用
    String bid; // ビッド額宣言用

    // 推測された相手の戦略パラメータ
    private double aggressiveBidThreshold = 0.5; // 相手のビッドが残金に占める割合の平均 (高いほど積極的)
    private int callCardThreshold = 7; // 相手がコールしたときのカードの平均値 (高いほど強いカードでしかコールしない)

    // 新しいラウンドカウンター
    private int roundCount = 0;
    private final int DEFENSIVE_ROUNDS_LIMIT = 20; // 守備的に振る舞うラウンド数

    KnowledgeClass() {
        for (int i = 0; i < history.length; i++) {
            history[i] = new InfoClass();
        }
    }

    // ビッド数の決定
    public String bid() {

        // ビッドする前にゲーム履歴情報を更新し、ラウンド数をインクリメント
        HistoryUpdate();
        roundCount++; // ラウンドごとにカウントを増やす

        // 相手の戦略を推測する
        inferOpponentStrategy();

        int b = 0; // ビッド額
        bid = ""; // 初期化

        // 先頭の20ラウンドは極力守備的に振る舞う
        if (roundCount <= DEFENSIVE_ROUNDS_LIMIT) {
            b = 1; // 常に最小ビッド額 (1)
            // 自分の残金を超えないように調整
            if (b > current.my_money) b = current.my_money;
            // 相手の残金を超えないように調整
            if (b > current.opponent_money) b = current.opponent_money;
        } else {
            // 20ラウンド経過後は通常のビッドロジック
            // 基本的なビッド額の計算
            b = Math.min(current.my_money, Math.min((current.my_money / 5) + 1,
                    (8 / (current.opponent_card + 1)) + 1));

            // 推測された相手の戦略に基づいてビッド額を調整
            if (current.my_card >= 8 && aggressiveBidThreshold < 0.3) {
                b = Math.min(current.my_money, current.my_money / 3); // 積極的にビッド
            } else if (current.my_card <= 3 && aggressiveBidThreshold > 0.7) {
                b = Math.min(current.my_money, current.my_money / 10); // 控えめにビッド
            }

            // ビッド額のチェック(自分の残金、相手の残金を超えた額は宣言できない)
            if (b > current.opponent_money)
                b = current.opponent_money;
            if (b > current.my_money)
                b = current.my_money;
        }


        // 返り値は String クラスで
        bid = "" + b;

        return bid;

    }

    // コール or ドロップの決定ルール
    public String decision() {

        decision = "n"; // 初期化
        // 相手の戦略を推測する
        inferOpponentStrategy();

        // 履歴 history から自分のカード mycard を予測する
        // 履歴から予測できない場合は初期値9としておく。
        int mycard = 9;

        // 現在の相手のビッド額と同じビッド額を、過去に相手が賭けていれば、
        // 自分のカードは、そのときのカードと同じであると予測する。
        for (int i = 0; i < history.length; i++) {
            if (current.opponent_bid == history[i].opponent_bid) {
                mycard = history[i].my_card;
                break;
            }
        }

        // 先頭の20ラウンドは極力守備的に振る舞う
        if (roundCount <= DEFENSIVE_ROUNDS_LIMIT) {
            // 自分のカードが非常に強い場合（例えば8か9）のみコールを検討
            // それ以外の場合はドロップ
            if (current.my_card >= 8) { // 8以上のカードならコールを検討
                decision = "c";
            } else {
                decision = "d"; // それ以外はドロップ
            }
        } else {
            // 20ラウンド経過後は通常の決定ロジック
            if (mycard != 9) { // 自分のカードが予測できた場合
                if (current.opponent_card > mycard) {
                    decision = "d"; // ドロップ
                } else {
                    if (current.opponent_card <= callCardThreshold && current.my_card > current.opponent_card) {
                        decision = "c";
                    } else if (current.opponent_card > callCardThreshold && current.my_card < current.opponent_card) {
                        decision = "d";
                    } else {
                        decision = "c";
                    }
                }
            } else { // 自分のカードが予測できなかった場合
                if (current.opponent_bid > current.my_money / 2 && aggressiveBidThreshold > 0.6) {
                    decision = "d";
                } else if (current.opponent_bid < current.opponent_money / 4 && aggressiveBidThreshold < 0.2) {
                    decision = "c";
                } else {
                    decision = "c"; // デフォルトでコール
                }
            }
        }

        // 返り値は String クラスで
        return decision;
    }


    // historyに直前のゲーム情報 previous を格納する
    private void HistoryUpdate() {

        // 履歴内の最古のゲーム情報を破棄する。
        for (int i = history.length - 2; i >= 0; i--) {
            history[i + 1] = CopyInfo(history[i]);
        }

        // 直前のゲーム情報を履歴に残す
        history[0] = CopyInfo(previous);

    }

    // 相手の戦略を推測する
    private void inferOpponentStrategy() {
        int totalBidsAnalyzed = 0;
        double sumBidRatio = 0.0;
        int totalCallsAnalyzed = 0;
        int sumCallCardValue = 0;

        for (InfoClass game : history) {
            if (game.opponent_card != 0) {
                if (game.opponent_bid > 0 && game.opponent_money > 0) {
                    sumBidRatio += (double) game.opponent_bid / game.opponent_money;
                    totalBidsAnalyzed++;
                }
                if (game.opponent_decision != null && game.opponent_decision.equals("c")) {
                    sumCallCardValue += game.opponent_card;
                    totalCallsAnalyzed++;
                }
            }
        }

        if (totalBidsAnalyzed > 0) {
            aggressiveBidThreshold = sumBidRatio / totalBidsAnalyzed;
        } else {
            aggressiveBidThreshold = 0.5;
        }

        if (totalCallsAnalyzed > 0) {
            callCardThreshold = sumCallCardValue / totalCallsAnalyzed;
        } else {
            callCardThreshold = 7;
        }
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