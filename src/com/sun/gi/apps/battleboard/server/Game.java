/*
 * $Id$
 *
 * Copyright 2006 Sun Microsystems, Inc. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * -Redistributions of source code must retain the above copyright
 * notice, this  list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 * notice, this list of conditions and the following disclaimer in
 * the documentation and/or other materials provided with the
 * distribution.
 *
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any
 * kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND
 * WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY
 * EXCLUDED. SUN AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY
 * DAMAGES OR LIABILITIES  SUFFERED BY LICENSEE AS A RESULT OF  OR
 * RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR
 * ITS DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE
 * FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT,
 * SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER
 * CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF
 * THE USE OF OR INABILITY TO USE SOFTWARE, EVEN IF SUN HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that Software is not designed, licensed or
 * intended for use in the design, construction, operation or
 * maintenance of any nuclear facility.
 */

package com.sun.gi.apps.battleboard.server;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.sun.gi.apps.battleboard.client.BattleBoard.positionValue.*;

/**
 *
 * @author  James Megquier
 * @version $Rev$, $Date$
 */
public class Game implements /* ChannelListener */ GLO {

    private static final long serialVersionUID = 1L;

    private static final Logger log =
	Logger.getLogger("com.sun.gi.apps.battleboard.server");

    protected String     gameName;
    protected ChannelID  channel;
    protected GLOReference<Game>                thisRef;
    protected LinkedList<GLOReference<Player>>  players;
    protected LinkedList<GLOReference<Player>>  spectators;
    protected Map<String, GLOReference<Board>>  playerBoards;
    protected GLOReference<Player>              currentPlayerRef;

    protected static int DEFAULT_BOARD_WIDTH  = 8;
    protected static int DEFAULT_BOARD_HEIGHT = 8;
    protected static int DEFAULT_BOARD_CITIES = 2;

    public static GLOReference create(
	    Collection<GLOReference<Player>> players) {

	SimTask task = SimTask.getCurrent();
	GLOReference<Game> ref = task.createGLO(new Game(players));

	ref.get(task).boot(ref);
	return ref;
    }

    protected Game(Collection<GLOReference<Player>> newPlayers) {

	SimTask task = SimTask.getCurrent();

	// XXX store and increment a next-channel-number in the GLO,
	// instead of using the current time(?) -jm
	gameName = "BB-" + System.currentTimeMillis();

	log.finer("Next game channel is `" + gameName + "'");

	players = new LinkedList<GLOReference<Player>>(newPlayers);
	Collections.shuffle(players);

	spectators = new LinkedList<GLOReference<Player>>();

	playerBoards = new HashMap<String, GLOReference<Board>>();
	for (GLOReference<Player> playerRef : players) {
	    Player p = playerRef.get(task);
	    playerBoards.put(p.getNickname(),
		createBoard(p.getNickname()));
	}

	channel = task.openChannel(gameName);
	task.lock(channel, true);
    }

    protected void boot(GLOReference<Game> ref) {
	SimTask task = SimTask.getCurrent();

	thisRef = ref;

	if (log.isLoggable(Level.FINE)) {
	    log.fine("playerBoards size " + playerBoards.size());
	    for (Map.Entry<String, GLOReference<Board>> x : playerBoards.entrySet()) {
		log.fine("playerBoard[" + x.getKey() + "]=`" +
		    x.getValue() + "'");
	    }
	}

	for (GLOReference<Player> playerRef : players) {
	    Player p = playerRef.get(task);
	    p.gameStarted(thisRef);
	}
	//task.addChannelMembershipListener(channel, thisRef);

	sendJoinOK();
	sendTurnOrder();
	startNextMove();
    }

    protected GLOReference<Board> createBoard(String playerName) {
	SimTask task = SimTask.getCurrent();

	Board board = new Board(playerName,
	    DEFAULT_BOARD_WIDTH, DEFAULT_BOARD_HEIGHT, DEFAULT_BOARD_CITIES);

	board.populate();

	GLOReference<Board> ref = task.createGLO(board,
	    gameName + "-board-" + playerName);

	log.finer("createBoard[" + playerName + "] returning " + ref);
	return ref;
    }

    public void endGame() {
	SimTask task = SimTask.getCurrent();
	log.info("Ending Game");
	// Tell all the players this game is over
	for (GLOReference<Player> ref : players) {
	    Player p = ref.get(task);
	    p.gameEnded(thisRef);
	}

	// Close this game's channel
	task.closeChannel(channel);
	channel = null;

	// Destroy all the players' boards
	for (GLOReference ref : playerBoards.values()) {
	    ref.delete(task);
	}

	// null the lists and maps so they can be GC'd
	players.clear();
	spectators.clear();
	playerBoards.clear();

	// Queue a reaper task to destroy this Game GLO
	// XXX do this...
    }

    protected void sendJoinOK() {
	SimTask task = SimTask.getCurrent();
	for (GLOReference<Player> ref : players) {
	    Player p = ref.peek(task);
	    task.join(p.getUID(), channel);
	    sendJoinOK(p);
	}
    }
    
    protected void sendJoinOK(Player player) {
	SimTask task = SimTask.getCurrent();
	ByteBuffer buf = ByteBuffer.allocate(1024);
	buf.put("ok ".getBytes());

	log.fine("playerBoards size " + playerBoards.size());

	GLOReference boardRef = playerBoards.get(player.getNickname());
	Board board = (Board) boardRef.peek(task);

	buf.put(Integer.toString(board.getWidth()).getBytes());
	buf.put(" ".getBytes());
	buf.put(Integer.toString(board.getHeight()).getBytes());
	buf.put(" ".getBytes());
	buf.put(Integer.toString(board.getStartCities()).getBytes());

	for (int i = 0; i < board.getWidth(); ++i) {
	    for (int j = 0; j < board.getHeight(); ++j) {
		if (board.getBoardPosition(i, j) == CITY) {
		    buf.put(" ".getBytes());
		    buf.put(Integer.toString(i).getBytes());
		    buf.put(" ".getBytes());
		    buf.put(Integer.toString(j).getBytes());
		}
	    }
	}

	task.sendData(channel, new UserID[] { player.getUID() },
	    buf.asReadOnlyBuffer(), true);
    }

    protected void sendTurnOrder() {
	SimTask task = SimTask.getCurrent();
	ByteBuffer buf = ByteBuffer.allocate(1024);
	buf.put("turn-order".getBytes());
	for (GLOReference<Player> playerRef : players) {
	    Player p = playerRef.peek(task);
	    buf.put(" ".getBytes());
	    buf.put(p.getNickname().getBytes());
	}
	broadcast(buf.asReadOnlyBuffer());
    }

    protected void broadcast(ByteBuffer buf) {
	SimTask task = SimTask.getCurrent();
	UserID[] uids = new UserID[players.size() + spectators.size()];

	int i = 0;
	for (GLOReference<Player> ref : players) {
	    Player p = ref.peek(task);
	    uids[i++] = p.getUID();
	}

	for (GLOReference<Player> ref : spectators) {
	    Player p = ref.peek(task);
	    uids[i++] = p.getUID();
	}

	log.info("Game: Broadcasting " + buf.position() +
	    " bytes on " + channel);

	task.sendData(channel, uids, buf, true);
    }

    protected void sendMoveStarted(Player player) {
	SimTask task = SimTask.getCurrent();
	ByteBuffer buf = ByteBuffer.allocate(1024);
	buf.put("move-started ".getBytes());
	buf.put(player.getNickname().getBytes());
	broadcast(buf.asReadOnlyBuffer());
    }

    protected void startNextMove() {
	SimTask task = SimTask.getCurrent();
	log.info("Running Game.startNextMove");

	currentPlayerRef = players.removeFirst();
	players.addLast(currentPlayerRef);
	Player p = currentPlayerRef.peek(task);
	sendMoveStarted(p);
    }

    protected void handlePass(Player player) {
	SimTask task = SimTask.getCurrent();
	ByteBuffer buf = ByteBuffer.allocate(1024);
	buf.put("move-ended ".getBytes());
	buf.put(player.getNickname().getBytes());
	buf.put(" pass".getBytes());
	broadcast(buf.asReadOnlyBuffer());

	startNextMove();
    }

    protected void handleMove(Player player, String[] tokens) {
	SimTask task = SimTask.getCurrent();

	String bombedPlayerNick = tokens[1];

	GLOReference<Board> boardRef = playerBoards.get(bombedPlayerNick);
	if (boardRef == null) {
	    log.warning(player.getNickname() +
		    " tried to bomb non-existant player " +
		    bombedPlayerNick);
	    handlePass(player);
	    return;

	}
	Board board = boardRef.get(task);

	int x = Integer.parseInt(tokens[2]);
	int y = Integer.parseInt(tokens[3]);

	// XXX check that x and y are in bounds

	Board.positionValue result = board.bombBoardPosition(x, y);

	String outcome = "";
	switch (result) {
	case HIT:
	    outcome = board.lost() ? "LOSS" : "HIT";
	    break;

	case NEAR:
	    outcome = "NEAR_MISS";
	    break;

	case MISS:
	    outcome = "MISS";
	    break;
	}

	ByteBuffer buf = ByteBuffer.allocate(1024);
	buf.put("move-ended ".getBytes());
	buf.put(player.getNickname().getBytes());
	buf.put(" bomb ".getBytes());
	buf.put(bombedPlayerNick.getBytes());
	buf.put(" ".getBytes());
	buf.put(Integer.toString(x).getBytes());
	buf.put(" ".getBytes());
	buf.put(Integer.toString(y).getBytes());
	buf.put(" ".getBytes());
	buf.put(outcome.getBytes());
	buf.put(" ".getBytes());

	broadcast(buf.asReadOnlyBuffer());

	// If the bombed player has lost, make them a spectator
	if (board.lost()) {
	    playerBoards.remove(bombedPlayerNick);
	    Iterator<GLOReference<Player>> i = players.iterator();
	    while (i.hasNext()) {
		GLOReference<Player> ref = i.next();
		Player p = ref.peek(task);
		if (bombedPlayerNick.equals(p.getNickname())) {
		    spectators.add(ref);
		    i.remove();
		}
	    }
	}

	// Check whether the player has won
	if (players.size() == 1) { // XXX: what if it's zero?
	    // queue a new task to handle end of game
	    try {
		task.queueTask(thisRef,
		    Game.class.getMethod("endGame"),
		    new Object[] { });
	    } catch (Exception e) {
		e.printStackTrace();
	    }

	    // They won, so don't start the next move
	    return;
	}

	startNextMove();
    }

    protected void handleResponse(GLOReference<Player> playerRef,
	    String[] tokens) {

	if (! playerRef.equals(currentPlayerRef)) {
	    log.severe("PlayerRef != CurrentPlayerRef");
	    return;
	}

	SimTask task = SimTask.getCurrent();
	Player player = playerRef.peek(task);
	String cmd = tokens[0];

	if ("pass".equals(cmd)) {
	    handlePass(player);
	} else if ("move".equals(cmd)) {
	    handleMove(player, tokens);
	} else {
	    log.warning("Unknown command `" + cmd + "'");
	    handlePass(player);
	}
    }

    /**
     * Handle data that was sent directly to the server.
     */
    public void userDataReceived(UserID uid, ByteBuffer data) {
	log.info("Game: Direct data from user " + uid);

	byte[] bytes = new byte[data.remaining()];
	data.get(bytes);
	String text = new String(bytes);

	log.info("userDataReceived: (" + text + ")");
	String[] tokens = text.split("\\s+");
	if (tokens.length == 0) {
	    log.warning("empty message");
	    return;
	}

	GLOReference<Player> playerRef = Player.getRef(uid);
	// XXX check for null

	handleResponse(playerRef, tokens);
    }

    // SimChannelMembershipListener methods

    public void joinedChannel(ChannelID cid, UserID uid) {
	log.info("Game: User " + uid + " joined channel " + cid);
    }

    public void leftChannel(ChannelID cid, UserID uid) {
	log.info("Game: User " + uid + " left channel " + cid);
    }

}
