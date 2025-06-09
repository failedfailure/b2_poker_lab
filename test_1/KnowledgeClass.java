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
            if (current.opponent_card > current.my_card || current.opponent_bid > current.my_money/3) {
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
            if (h.opponent_bid == current.opponent_bid) {
                sameBidCount++;
                cardSum += h.opponent_card;
            }
        }
        
        return sameBidCount > 0 ? cardSum / sameBidCount : 5; // デフォルト値5
    }

    // ブラフ検出メソッド
    private boolean detectBluffPattern() {
        int highBidLowCard = 0;
        int totalCases = 0;
        
        for (InfoClass h : history) {
            if (h.opponent_card < 5 && h.opponent_bid > h.opponent_money/3) {
                highBidLowCard++;
            }
            totalCases++;
        }
        
        return totalCases > 0 && highBidLowCard * 2 > totalCases; // 50%超でブラフ判定
    }

    private void HistoryUpdate() {
        for (int i = history.length - 2; i >= 0; i--) {
            history[i + 1] = CopyInfo(history[i]);
        }
        history[0] = CopyInfo(previous);
    }

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