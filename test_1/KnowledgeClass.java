/* 知識クラス */
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
        if (roundCounter <= 20) {
            // フェーズ1: 保守戦略 (最小ビッド)
            b = 1;
        } 
        else if (roundCounter % 30 == 0) {
            // フェーズ3: 30ラウンドごとのランダム干渉
            b = random.nextInt(Math.min(current.my_money, current.opponent_money)) + 1;
        }
        else {
            // フェーズ2: 学習反演戦略
            int predictedOpponentCard = predictOpponentCard();
            
            // 相手のカード予測値に基づく動的ビッド
            if (predictedOpponentCard > 7) {
                b = Math.min(current.my_money/2 + 1, Math.min(current.my_money, current.opponent_money));
            } else if (predictedOpponentCard > 4) {
                b = Math.min(current.my_money/3 + 1, Math.min(current.my_money, current.opponent_money));
            } else {
                b = Math.min(current.my_money/4 + 1, Math.min(current.my_money, current.opponent_money));
            }
        }

        // ビッド額のチェック
        if (b > current.opponent_money) b = current.opponent_money;
        if (b > current.my_money) b = current.my_money;
        if (b < 1) b = 1; // 最低ビッド額保証

        bid = "" + b;
        return bid;
    }

    public String decision() {
        decision = "n";

        // 30ラウンドごとのランダム干渉
        if (roundCounter % 30 == 0) {
            return random.nextBoolean() ? "c" : "d";
        }

        // フェーズ1: 保守戦略 (生存優先)
        if (roundCounter <= 20) {
            // 相手のカードが強い or ビッド額が自己資金の1/3超ならドロップ
            if (current.opponent_card > current.my_card || 
                current.opponent_bid > current.my_money/3) {
                decision = "d";
            } else {
                decision = "c";
            }
        } 
        // フェーズ2: 学習反演戦略
        else {
            int predictedOpponentCard = predictOpponentCard();
            
            // 相手の行動パターン分析
            boolean isBluffing = detectBluffPattern();
            
            // 相手のカードが強く、ブラフでない場合はドロップ
            if (predictedOpponentCard > current.my_card && !isBluffing) {
                decision = "d";
            } else {
                decision = "c";
            }
        }

        return decision;
    }

    // 相手のカード予測メソッド
    private int predictOpponentCard() {
        // 相手のビッド額とカードの相関分析
        int sameBidCount = 0;
        int cardSum = 0;
        
        for (InfoClass h : history) {
            if (h.opponent_bid == current.opponent_bid && h.opponent_card > 0) {
                sameBidCount++;
                cardSum += h.opponent_card;
            }
        }
        
        // デフォルト値5（中央値）
        return sameBidCount > 0 ? cardSum / sameBidCount : 5;
    }

    // ブラフ検出メソッド
    private boolean detectBluffPattern() {
        int highBidLowCard = 0;
        int totalCases = 0;
        
        for (InfoClass h : history) {
            // カード情報が有効な場合のみ分析
            if (h.opponent_card > 0 && h.opponent_bid > 0) {
                // 低カード(1-5)で高ビッド(自己資金の1/3超)を検出
                if (h.opponent_card < 6 && h.opponent_bid > h.opponent_money/3) {
                    highBidLowCard++;
                }
                totalCases++;
            }
        }
        
        // 50%超でブラフ判定
        return totalCases > 2 && highBidLowCard * 2 > totalCases;
    }

    private void HistoryUpdate() {
        // 履歴をシフト
        for (int i = history.length - 2; i >= 0; i--) {
            history[i + 1] = CopyInfo(history[i]);
        }
        // 直前のゲーム情報を履歴に追加
        history[0] = CopyInfo(previous);
    }

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