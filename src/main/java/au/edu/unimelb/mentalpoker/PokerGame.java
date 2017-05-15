package au.edu.unimelb.mentalpoker;


import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static au.edu.unimelb.mentalpoker.PokerGame.PlayerAction.Type.BET;
import static au.edu.unimelb.mentalpoker.PokerGame.PlayerAction.Type.CHECK;
import static au.edu.unimelb.mentalpoker.PokerGame.PlayerAction.Type.FOLD;
import static java.lang.Math.max;
import static java.lang.Math.min;


public class PokerGame extends Thread {
    private static final int INITIAL_BALANCE = 1000000;
    private static final int CARDS_PER_HAND = 2;

    private final MentalPokerEngine poker;
    private final PeerNetwork network;

    private List<PlayerInfo> playerInfoList;

    private static class PlayerInfo {
        boolean isFolded;
        int balance;
        int stake;

        public PlayerInfo(int balance) {
            this(false /* isFolded */, balance, 0 /* stake */);
        }

        public PlayerInfo(boolean isFolded, int balance, int stake) {
            this.isFolded = isFolded;
            this.balance = balance;
            this.stake = stake;
        }

        public boolean isFolded() {
            return isFolded;
        }

        public boolean canPlay() {
            return balance > 0;
        }
    }

    public static class PlayerAction {
        private static final PlayerAction FOLD = new PlayerAction(Type.FOLD, 0);
        private static final PlayerAction CHECK = new PlayerAction(Type.CHECK, 0);

        private final Type actionType;
        private final int betAmount;

        public enum Type {
            FOLD,
            CHECK,
            BET;
        }

        private PlayerAction(Type actionType, int betAmount) {
            this.actionType = actionType;
            this.betAmount = betAmount;
        }

        public static PlayerAction bet(int betAmount) {
            return new PlayerAction(BET, betAmount);
        }

        public static PlayerAction check() {
            return CHECK;
        }

        public static PlayerAction fold() {
            return FOLD;
        }

        public Type getActionType() {
            return actionType;
        }

        public int getBetAmount() {
            return betAmount;
        }
    }

    public PokerGame(PeerNetwork network, MentalPokerEngine poker) {
        this.network = network;
        this.poker = poker;
    }

    @Override
    public void run() {
        init();

        for (int i = 0; i < 3; i++) {
            playHand();
        }

        try {
            poker.finish();
            System.out.println("Game finished successfully.");
        } catch (CheatingDetectedException e) {
            System.out.println("Cheating detected.");
        }
    }

    private void init() {
        System.out.println("Shuffling...");
        poker.init();

        playerInfoList = new ArrayList<>(poker.getNumPlayers());
        for (int i = 0; i < poker.getNumPlayers(); i++) {
            playerInfoList.add(new PlayerInfo(INITIAL_BALANCE));
        }
    }

    private void playHand() {
        resetHand();
        doDealing();

        display();
        if (doBetting()) {
            poker.rake();
            return;
        }

        poker.drawPublic();
        poker.drawPublic();
        poker.drawPublic();

        display();
        if (doBetting()) {
            poker.rake();
            return;
        }

        poker.drawPublic();

        display();
        if (doBetting()) {
            poker.rake();
            return;
        }

        poker.drawPublic();

        display();
        if (doBetting()) {
            poker.rake();
            return;
        }

//        checkWinner();

        poker.rake();
    }

    private void resetHand() {
        for (PlayerInfo playerInfo : playerInfoList) {
            playerInfo.stake = 0;
            playerInfo.isFolded = false;
        }
    }

    private int getPotAmount() {
        int result = 0;
        for (PlayerInfo playerInfo : playerInfoList) {
            result += playerInfo.stake;
        }
        return result;
    }

    private void display() {
        System.out.println("Pot amount is $" + getPotAmount());
        displayPlayerHand();
        displayPublicCards();
    }

    private void displayPlayerHand() {
        final List<Card> playerHand = poker.getLocalPlayerCards();
        final StringBuilder sb = new StringBuilder("Your cards: ");
        for (Card card : playerHand) {
            sb.append(card);
            sb.append(" ");
        }
        System.out.println(sb);
    }

    private void displayPublicCards() {
        final List<Card> cards = poker.getPublicCards();
        final StringBuilder sb = new StringBuilder("Table cards: ");
        for (Card card : cards) {
            sb.append(card);
            sb.append(" ");
        }
        System.out.println(sb);
    }

    private void doDealing() {
        for (int i = 0; i < poker.getNumPlayers(); i++) {
            final PlayerInfo playerInfo = playerInfoList.get(i);
            final int playerId = i + 1;

            if (!playerInfo.canPlay()) {
                continue;
            }

            for (int j = 0; j < CARDS_PER_HAND; j++) {
                poker.draw(playerId);
            }
        }
    }

    private boolean doBetting() {
        boolean allCalledOrFolded = false;

        while (!allCalledOrFolded) {
            boolean anyRaised = false;

            for (int i = 0; i < poker.getNumPlayers(); i++) {
                final PlayerInfo playerInfo = playerInfoList.get(i);
                final int playerId = i + 1;
                final int minBet = max(0, getMaxStake() - playerInfo.stake);

                if (playerInfo.isFolded()) {
                    continue;
                }

                PlayerAction action;
                if (playerId == network.getLocalPlayerId()) {
                    System.out.println("YOUR TURN:");
                    action = getActionFromUser(minBet);
                    broadcastPlayerAction(action);
                } else {
                    action = receivePlayerAction(playerId);
                }

                if (action.getActionType() == BET) {
                    System.out.printf("Player %d bet $%d\n", i + 1, action.getBetAmount());
                    playerInfo.balance -= action.getBetAmount();
                    playerInfo.stake += action.getBetAmount();

                    if (action.getBetAmount() > minBet) {
                        anyRaised = true;
                    }
                } else if (action.getActionType() == CHECK) {
                    System.out.printf("Player %d checked\n", i + 1);
                    assert(minBet == 0);
                } else if (action.getActionType() == FOLD) {
                    System.out.printf("Player %d folded\n", i + 1);
                    playerInfo.isFolded = true;
                    if (maybePayWinner()) {
                        return true;
                    }
                }
            }

            allCalledOrFolded = !anyRaised;
        }

        return false;
    }

    private int getMaxStake() {
        int result = 0;
        for (PlayerInfo playerInfo : playerInfoList) {
            result = max(result, playerInfo.stake);
        }
        return result;
    }

    private boolean maybePayWinner() {
        if (getNumPlayersStillIn() == 1) {
            payWinner(getWinner());
            return true;
        }
        return false;
    }

    private void payWinner(final int winnerIndex) {
        final PlayerInfo winnerInfo = playerInfoList.get(winnerIndex);
        int totalWinnerTakings = 0;

        for (int i = 0; i < playerInfoList.size(); i++) {
            if (i == winnerIndex) {
                continue;
            }

            final PlayerInfo playerInfo = playerInfoList.get(i);
            final int winnerTakings = min(winnerInfo.stake, playerInfo.stake);
            totalWinnerTakings += winnerTakings;
            playerInfo.balance += playerInfo.stake - winnerTakings;
            playerInfo.stake = 0;
        }

        winnerInfo.balance += totalWinnerTakings;
        winnerInfo.balance += winnerInfo.stake;
        winnerInfo.stake = 0;

        System.out.printf("Player %d won $%d\n", winnerIndex + 1, totalWinnerTakings);
    }

    /**
     * Gets the index of the last player still in the hand.
     *
     * <p> This method should only be called after it has been determined that all players except one have folded.
     */
    private int getWinner() {
        for (int i = 0; i < playerInfoList.size(); i++) {
            if (!playerInfoList.get(i).isFolded) {
                return i;
            }
        }
        throw new RuntimeException();
    }

    private int getNumPlayersStillIn() {
        int result = 0;
        for (PlayerInfo playerInfo : playerInfoList) {
            if (!playerInfo.isFolded()) {
                result++;
            }
        }
        return result;
    }

    private void broadcastPlayerAction(PlayerAction playerAction) {
        Proto.PlayerActionMessage.Type actionType;
        if (playerAction.getActionType() == BET) {
            actionType = Proto.PlayerActionMessage.Type.BET;
        } else if (playerAction.getActionType() == CHECK) {
            actionType = Proto.PlayerActionMessage.Type.CHECK;
        } else {
            actionType = Proto.PlayerActionMessage.Type.FOLD;
        }

        Proto.NetworkMessage msg =
                Proto.NetworkMessage.newBuilder()
                        .setType(Proto.NetworkMessage.Type.PLAYER_ACTION)
                        .setPlayerActionMessage(
                                Proto.PlayerActionMessage.newBuilder()
                                        .setType(actionType)
                                        .setBetAmount(playerAction.getBetAmount()))
                        .build();

        network.broadcast(msg);
    }

    private PlayerAction receivePlayerAction(int playerId) {
        Proto.NetworkMessage msg = network.receive(playerId);
        Proto.PlayerActionMessage actionMessage = msg.getPlayerActionMessage();

        if (actionMessage.getType() == Proto.PlayerActionMessage.Type.BET) {
            return PlayerAction.bet(actionMessage.getBetAmount());
        } else if (actionMessage.getType() == Proto.PlayerActionMessage.Type.CHECK) {
            return PlayerAction.check();
        } else {
            return PlayerAction.fold();
        }
    }

    private PlayerAction getActionFromUser(int minBet) {
        final Scanner scanner = new Scanner(System.in);

        while (true) {
            if (minBet == 0) {
                System.out.printf("Bet, Check or Fold (b/c/f)? ");
                System.out.flush();
            } else {
                System.out.printf("Min bet is $%d. Bet or Fold (b/f)? ", minBet);
                System.out.flush();
            }

            String cmd = scanner.nextLine();
            if ("b".equals(cmd)) {
                return PlayerAction.bet(getBetAmount(minBet));
            } else if ("c".equals(cmd)) {
                if (minBet == 0) {
                    return PlayerAction.check();
                }
            } else if ("f".equals(cmd)) {
                return PlayerAction.fold();
            }
        }
    }

    private int getBetAmount(int minBet) {
        final int balance = getLocalPlayerBalance();
        final Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.printf("You have $%d in chips. How much do you want to bet? ", getLocalPlayerBalance());
            System.out.flush();

            try {
                int amount = Integer.parseInt(scanner.nextLine());
                if (amount > 0 && amount == balance || amount >= minBet && amount <= balance) {
                    return amount;
                }
            } catch (NumberFormatException e) {
                // Just prompt the user again.
            }
        }
    }

    private int getLocalPlayerBalance() {
        return playerInfoList.get(network.getLocalPlayerId() - 1).balance;
    }
}
