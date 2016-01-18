package pl.pszty.checkers.ai;

import pl.pszty.checkers.core.Board;
import pl.pszty.checkers.core.Gameboard;
import pl.pszty.checkers.core.Move;
import pl.pszty.checkers.enums.FieldState;
import pl.pszty.checkers.enums.Player;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Michal Created by Michal on 13.01.2016.
 */
public class BoardState {

    private long randomNumberedTable[][];
    private Map<Long, List<TranspositionTableCell>> transpositionTable;
    private Path rnTableFile;
    private Gameboard gameboard;
    private Player player;

    public BoardState() {
        String os = System.getProperty("os.name");

        if (os.startsWith("Windows")) {
            this.rnTableFile = Paths.get("C:\\Users\\Public\\Documents\\bst.csv");
        } else {
            this.rnTableFile = Paths.get("/tmp/bst.csv");
        }

        if (this.rnTableFile.toFile().isFile()) {
            try {
                this.readTable();
            } catch (IOException exce) {
                System.out.println("Nie mozna zapisac pliku tablicy.");
                System.out.println("IOException: " + exce.getMessage());
            }
        } else {
            try {
                Files.write(this.rnTableFile, this.createTable(), Charset.defaultCharset());
            } catch (IOException exce) {
                System.out.println("Nie mozna zapisac pliku tablicy.");
                System.out.println("IOException: " + exce.getMessage());
            }
        }
        this.gameboard = Gameboard.getInstance();
        this.player = gameboard.getHumanPlayer();
        this.transpositionTable = new HashMap<>();
    }

    private List<String> createTable() {
        List<String> rnTableString = new ArrayList<>();
        String toList;
        Random random = new Random();
        this.randomNumberedTable = new long[32][5];
        BigInteger temp;
        for (int row = 0; row < 32; row++) {
            toList = "";
            for (int column = 0; column < 5; column++) {
                temp = new BigInteger(64, random);
                this.randomNumberedTable[row][column] = temp.longValue();
                toList += (this.randomNumberedTable[row][column] + ";");
            }
            rnTableString.add(toList);
        }
        return rnTableString;
    }

    private List<String> readTable() throws IOException {
        List<String> fromFile = Files.lines(this.rnTableFile).map(line -> line.split(";")).flatMap(Arrays::stream).collect(Collectors.toList());
        this.randomNumberedTable = new long[32][5];
        int row = 0, column = 0;
        for (int index = 0; index < 160; index++) {
            this.randomNumberedTable[row][column] = new Long(fromFile.get(index));
            System.out.println(this.randomNumberedTable[row][column]);
            column = ++column % 5;
            if (column == 0) {
                ++row;
            }
        }
        return fromFile;
    }

    public void printRnTable() {
        for (int row = 0; row < 32; row++) {
            for (int column = 0; column < 5; column++) {
                System.out.print(this.randomNumberedTable[row][column] + " ");
            }
            System.out.println();
        }
    }

    private long countHashFunction(Board board) {
        FieldState[][] fieldStates = board.getBoard();
        long sum = 0;
        for (int row = 0; row < 8; row++) {
            for (int column = 0; column < 8; column++) {
                if ((row + column) % 2 != 0) {
                    sum = (this.randomNumberedTable[(row + column) / 2][fieldStates[row][column].getValue()]);
                }
            }
        }
        return sum;
    }

    private long getNextMoveHash(long hash, Move nextMove, Board board) {

        FieldState[][] fieldStates = board.getBoard();
        int fromColumn = nextMove.getFromColumn();
        int fromRow = nextMove.getFromRow();
        int toColumn = nextMove.getToColumn();
        int toRow = nextMove.getToRow();
        int beatingRow = nextMove.getBeatingRow();
        int beatingColumn = nextMove.getBeatingColumn();

        FieldState fromFigure = fieldStates[fromRow][fromColumn];
        FieldState toFigure = fieldStates[toRow][toColumn];

        long newHash = hash ^ (this.randomNumberedTable[(fromRow + fromColumn) / 2][fromFigure.getValue()]);
        newHash = newHash ^ (this.randomNumberedTable[(toRow + toColumn) / 2][toFigure.getValue()]);

        newHash = newHash ^ (this.randomNumberedTable[(fromRow + fromColumn) / 2][toFigure.getValue()]);
        newHash = newHash ^ (this.randomNumberedTable[(toRow + toColumn) / 2][fromFigure.getValue()]);

        if ((beatingColumn != 0) && (beatingRow != 0)) {
            FieldState beatedFigure = fieldStates[beatingRow][beatingColumn];
            newHash = newHash ^ (this.randomNumberedTable[(beatingRow + beatingColumn) / 2][beatedFigure.getValue()]);
            newHash = newHash ^ (this.randomNumberedTable[(beatingRow + beatingColumn) / 2][FieldState.empty.getValue()]);
        }

        return newHash;
    }

    private void insertTranspositionTableCell(long hash, int alpha, int beta, Move alphaMove, Move betaMove, int depth, Board board) {
        TranspositionTableCell transpositionTableCell = new TranspositionTableCell(alpha, beta, alphaMove, betaMove, depth, board);
        if (!transpositionTable.containsKey(hash)) {
            transpositionTable.put(hash, new LinkedList<>());
        }
        transpositionTable.get(hash).add(transpositionTableCell);
    }

    public void minMaxAlphaBeta(long state, Board board, int depth) {
        TranspositionTableCell transpositionTableCell = null;
        if (this.transpositionTable.containsKey(state)) {
            for (TranspositionTableCell ttc : this.transpositionTable.get(state)) {
                if (ttc.getBoard().equals(board)) {
                    transpositionTableCell = ttc;
                }
            }
        }
        if (transpositionTableCell != null) {
            if (transpositionTableCell.getSearchingDepth() >= 1) {
                return;
            }
        }
        alphaBeta(state, board, depth, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    private Integer alphaBeta(long state, Board board, int depth, Integer alpha, Integer beta) {
        //istnieje juz taki stan w tabeli transpozycji
        if (this.transpositionTable.containsKey(state)) {
            TranspositionTableCell transpositionTableCell;
            for (TranspositionTableCell ttc : this.transpositionTable.get(state)) {
                if (ttc.getBoard().equals(board)) {
                    transpositionTableCell = ttc;
                    if (transpositionTableCell.getSearchingDepth() >= depth) {
                        if (board.getActivePlayer().equals(this.player)) {  //na sztywno -> gracz jest bialy
                            return transpositionTableCell.getAlpha();   //w dokumentacji beta
                        } else {
                            return transpositionTableCell.getBeta();    //w dokmentacji alpha
                        }
                    }
                }
            }

        }
        if (!board.tellMeTheWinner().equals(Player.none) || (depth == 0)) {
            return board.getBoardEvaulation();
        }
        //algorytm realizowany z punktu widzenia gracza; im wieksza
        if (board.getActivePlayer().equals(this.player)) {   //na sztywno -> biały to gracz
            Map<Board, Move> possibleMoves = board.getPossibleMoves();
            Set<Board> possibleBoards = possibleMoves.keySet();
            Move maxMove = null;
            for (Board child : possibleBoards) {
                long hash = getNextMoveHash(state, possibleMoves.get(child), board);
                int temp = alphaBeta(hash, child, depth - 1, alpha, beta);
                if (temp >= alpha) {
                    alpha = temp;
                    maxMove = possibleMoves.get(child);
                }
                if (alpha >= beta) {
                    insertTranspositionTableCell(state, alpha, beta, maxMove, null, depth, board);
                    return beta;
                }
            }
            insertTranspositionTableCell(state, alpha, beta, maxMove, null, depth, board);
            return alpha;
        } else {
            Map<Board, Move> possibleMoves = board.getPossibleMoves();
            Set<Board> possibleBoards = possibleMoves.keySet();
            Move minMove = null;
            for (Board child : possibleBoards) {
                long hash = getNextMoveHash(state, possibleMoves.get(child), board);
                int temp = alphaBeta(hash, child, depth - 1, alpha, beta);
                if (temp <= beta) {
                    beta = temp;
                    minMove = possibleMoves.get(child);
                }
                if (alpha >= beta) {
                    insertTranspositionTableCell(state, alpha, beta, null, minMove, depth, board);
                    return alpha;
                }
            }
            insertTranspositionTableCell(state, alpha, beta, null, minMove, depth, board);
            return beta;
        }
    }

    public void performThinkingAndMove() {
        Board board = gameboard.getCoppyOfOfficialBoard();
        long hash = countHashFunction(board);
        minMaxAlphaBeta(hash, gameboard.getCoppyOfOfficialBoard(), 7);
        TranspositionTableCell transpositionTableCell = null;
        for (TranspositionTableCell ttc : this.transpositionTable.get(hash)) {
            if (ttc.getBoard().equals(board)) {
                transpositionTableCell = ttc;
                if (this.player.equals(Player.black)) {
                    gameboard.performWhitePlayerMovement(transpositionTableCell.getBetaMove());
                } else {
                    gameboard.performBlackPlayerMovement(transpositionTableCell.getBetaMove());
                    System.out.println(transpositionTableCell.getSearchingDepth());
                    gameboard.displayOfficialBoard();
                }
            }
        }

    }
}
