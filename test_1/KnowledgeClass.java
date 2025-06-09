
/* 知識クラス */

//import java.*;
import java.lang.Math;
import java.util.Random;

public class KnowledgeClass {

	InfoClass current = new InfoClass(); // ゲーム情報
	InfoClass previous = new InfoClass(); // 直前のゲーム情報

	// 過去10回分のゲーム履歴情報
	// n回前のゲーム情報をhistory[n-1]に記憶
	InfoClass[] history = new InfoClass[10];

	String decision; // コールorドロップの宣言用
	String bid; // ビッド額宣言用

	int roundCount = 0; // 現在のラウンド数
	Random rand = new Random(); // ランダム戦略用

	KnowledgeClass() {
		for (int i = 0; i < history.length; i++) {
			history[i] = new InfoClass();
		}
	}

	// ビッド数の決定
	public String bid() {

		// ラウンド数をインクリメント
		roundCount++;

		// ビッドする前にゲーム履歴情報を更新する
		HistoryUpdate();

		int b = 0; // ビッド額
		bid = ""; // 初期化

		// --- ランダム戦略（30の倍数ラウンド） ---
		if (roundCount % 30 == 0) {
			// ランダムなビッド（1〜自分の残高の範囲で）
			b = rand.nextInt(current.my_money + 1);
			if (b > current.opponent_money) b = current.opponent_money;
			bid = "" + b;
			return bid;
		}

		// --- 保守戦略（1〜20ラウンド） ---
		if (roundCount <= 20) {
			// 相手のカードが高い（>3）ならば小さくビッド
			if (current.opponent_card > 3) {
				b = 1;
			} else {
				b = 2;
			}
			bid = "" + Math.min(b, Math.min(current.my_money, current.opponent_money));
			return bid;
		}

		// --- 学習戦略（21ラウンド以降） ---
		// 相手の平均カード値を履歴から算出し推測
		double opponentCardSum = 0;
		int validCount = 0;
		for (int i = 0; i < history.length; i++) {
			if (history[i].opponent_card >= 0) {
				opponentCardSum += history[i].opponent_card;
				validCount++;
			}
		}
		double opponentAvg = validCount > 0 ? opponentCardSum / validCount : 3;

		// 自分のカードが平均より高ければ強気に、低ければ控えめに
		if (current.my_card > opponentAvg) {
			b = Math.min(current.my_money, Math.min(5, current.opponent_money));
		} else {
			b = 1;
		}

		bid = "" + b;
		return bid;
	}

	// コール or ドロップの決定ルール
	public String decision() {

		decision = "n"; // 初期化

		// --- ランダム戦略（30の倍数ラウンド） ---
		if (roundCount % 30 == 0) {
			decision = rand.nextBoolean() ? "y" : "n";
			return decision;
		}

		// --- 保守戦略（1〜20ラウンド） ---
		if (roundCount <= 20) {
			if (current.opponent_card > 3) {
				decision = "n"; // 弱い手札なら降りる
			} else {
				decision = "y";
			}
			return decision;
		}

		// --- 学習戦略（21ラウンド以降） ---
		double opponentCardSum = 0;
		int validCount = 0;
		for (int i = 0; i < history.length; i++) {
			if (history[i].opponent_card >= 0) {
				opponentCardSum += history[i].opponent_card;
				validCount++;
			}
		}
		double opponentAvg = validCount > 0 ? opponentCardSum / validCount : 3;

		if (current.my_card > opponentAvg) {
			decision = "y";
		} else {
			decision = "n";
		}

		return decision;
	}

	// 履歴の更新
	public void HistoryUpdate() {
		for (int i = history.length - 1; i >= 1; i--) {
			history[i] = history[i - 1];
		}
		history[0] = previous;
	}
}
