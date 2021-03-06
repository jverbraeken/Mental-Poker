package au.edu.unimelb.mentalpoker.algorithm;

import au.edu.unimelb.mentalpoker.poker.Card;
import au.edu.unimelb.mentalpoker.exceptions.CheatingDetectedException;
import au.edu.unimelb.mentalpoker.Hand;
import au.edu.unimelb.mentalpoker.PokerGame;
import com.google.common.collect.ImmutableList;

import java.util.concurrent.TimeoutException;

/**
 * This interface defines the services provided by a mental poker protocol. {@link PokerGame} makes use of a
 * {@link MentalPokerEngine} to perform secure dealing, shuffling and handling of cards.
 */
public interface MentalPokerEngine {
    /** Initialises the Mental Poker engine ready for dealing. */
    void init() throws TimeoutException;

    /** Gets the number of cards left in the deck to be dealt. */
    int getNumCardsLeft();

    /** Deals a secret card to the player with ID {@code playerId}. */
    void draw(int playerId) throws TimeoutException;

    /** Deals a face-up card to the table. */
    void drawPublic() throws TimeoutException;

    /** Reveals the face value of all the cards in a players hand. */
    void open(int playerId) throws TimeoutException;

    /** Clears the hands of all players. */
    void rake();

    /** Completes the game, performing any necessary validity verification. */
    void finish() throws CheatingDetectedException, TimeoutException;

    /** Gets the Hand of the player with ID {@code playerId}. */
    Hand getPlayerHand(int playerId);

    /** Gets the cards dealt to the table. */
    ImmutableList<Card> getPublicCards();

    /** Gets a list of the cards in the local player's hand. */
    ImmutableList<Card> getLocalPlayerCards();

    /** Gets the number of players in the game. */
    int getNumPlayers();
}
