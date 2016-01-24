package controllers.adjMCTS;

import java.util.ArrayList;
import java.util.Random;

import core.game.StateObservation;
import core.player.AbstractPlayer;
import ontology.Types;
import ontology.Types.ACTIONS;
import tools.ElapsedCpuTimer;
import tools.IO;

/**
 * Created with IntelliJ IDEA.
 * User: ssamot
 * Date: 14/11/13
 * Time: 21:45
 * This is a Java port from Tom Schaul's VGDL - https://github.com/schaul/py-vgdl
 */
public class Agent extends AbstractPlayer {

	public static boolean wholeTree = true;
	public static double mixmax = 0;
	public static double selectionBonus = 0;
	public static double sigmaRayleigh = 2;
	public static double expansionProb = 0;
	public static double randomProb = 0;
	public static String currentGame;
	
	public static int previousAction = -1;
	public static RowOdds previousRepetition = new RowOdds();
	public static RowOdds[] dataBonus;
	
    public static int NUM_ACTIONS;
    public static int ROLLOUT_DEPTH = 10;
    public static double K = Math.sqrt(2);
    public static Types.ACTIONS[] actions;

    /**
     * Random generator for the agent.
     */
    private SingleMCTSPlayer mctsPlayer;

    /**
     * Public constructor with state observation and time due.
     * @param so state observation of the current game.
     * @param elapsedTimer Timer for the controller creation.
     */
    public Agent(StateObservation so, ElapsedCpuTimer elapsedTimer)
    {
    	previousAction = -1;
    	previousRepetition = new RowOdds();
    	
        //Get the actions in a static array.
        ArrayList<Types.ACTIONS> act = so.getAvailableActions(true);
        actions = new Types.ACTIONS[act.size()];
        for(int i = 0; i < actions.length; ++i)
        {
            actions[i] = act.get(i);
        }
        NUM_ACTIONS = actions.length;

        //Create the player.
        mctsPlayer = new SingleMCTSPlayer(new Random());
        
        IO reader = new IO();
        String[] values = reader.readFile("oddsdata/" + currentGame + "_action_rowodds.csv");
        
        dataBonus = new RowOdds[values.length];
        for(int i=0; i<dataBonus.length; i++){
        	dataBonus[i] = new RowOdds();
        }
        
        for (int i=1; i<values.length; i++){
        	String[] data = values[i].split(",");
        	if(data.length <= 1){
        		continue;
        	}
        	dataBonus[i].actionNil = Double.parseDouble(data[1]);
        	dataBonus[i].actionHold = Double.parseDouble(data[2]);
        	dataBonus[i].actionNewAction = Double.parseDouble(data[3]);
        }
        
        values = reader.readFile("oddsdata/" + currentGame + "_nil_rowodds.csv");
        for (int i=1; i<values.length; i++){
        	String[] data = values[i].split(",");
        	if(data.length <= 1){
        		continue;
        	}
        	dataBonus[i].nilHold = Double.parseDouble(data[1]);
        	dataBonus[i].nilAction = Double.parseDouble(data[2]);
        }
    }

    /**
     * Picks an action. This function is called every game step to request an
     * action from the player.
     * @param stateObs Observation of the current state.
     * @param elapsedTimer Timer when the action returned is due.
     * @return An action for the current state
     */
    public Types.ACTIONS act(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {

        //Set the state observation object as the new root of the tree.
        mctsPlayer.init(stateObs);

        //Determine the action using MCTS...
        int action = mctsPlayer.run(elapsedTimer);
        
        ACTIONS tempPrev = ACTIONS.ACTION_NIL;
        if(previousAction >= 0){
        	tempPrev = Agent.actions[previousAction];
        }
        if(tempPrev == ACTIONS.ACTION_NIL){
        	if(action == previousAction){
        		previousRepetition.actionHold = 0;
        		previousRepetition.nilHold += 1;
            }
            else{
            	previousRepetition.actionHold = 1;
            	previousRepetition.nilHold = 0;
            	previousRepetition.nilAction += 1;
            }
        }
        else{
        	if(action != previousAction){
        		if(Agent.actions[action] == ACTIONS.ACTION_NIL){
        			previousRepetition.actionHold = 0;
                	previousRepetition.nilHold = 1;
                	previousRepetition.actionNil += 1;
        		}
        		else{
        			previousRepetition.actionHold = 1;
                	previousRepetition.nilHold = 0;
                	previousRepetition.actionNewAction += 1;
        		}
        	}
        	else{
        		previousRepetition.actionHold += 1;
            	previousRepetition.nilHold = 0;
        	}
        }
        
        previousAction = action;
        
        //... and return it.
        return actions[action];
    }

}
