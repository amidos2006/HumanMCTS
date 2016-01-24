package controllers.oldAdjMCTS;

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
	public static int actionRepeat = 0;
	public static int nilRepeat = 0;
	public static ArrayList<Double> actionBonus;
	public static ArrayList<Double> nilBonus;
	
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
    	actionRepeat = 0;
    	nilRepeat = 0;
    	
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
        
        actionBonus = new ArrayList<Double>();
        nilBonus = new ArrayList<Double>();
        
        IO reader = new IO();
        String[] values = reader.readFile("action.txt")[0].split(",");
        
        for(int i=0; i<values.length; i++){
        	actionBonus.add(Double.parseDouble(values[i]));
        }
        
        values = reader.readFile("nil.txt");
        nilBonus.add(0.0);
        for (int i=1; i<values.length; i++){
        	nilBonus.add(Double.parseDouble(values[i]));
        }
    }
    
    public static double getBonus(ArrayList<Double> dist, int point){
    	if(point >= dist.size()){
    		return 0;
    	}
    	double result = 0;
    	for(int i=0; i <= point; i++){
    		result += dist.get(i);
    	}
    	
    	return 1 - result;
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
        		nilRepeat += 1;
            }
            else{
            	nilRepeat = 0;
            	actionRepeat = 1;
            }
        }
        else{
        	if(action == previousAction){
        		actionRepeat += 1;
        	}
        	else{
        		if(Agent.actions[action] == ACTIONS.ACTION_NIL){
        			actionRepeat = 0;
        			nilRepeat = 1;
        		}
        		else{
        			actionRepeat = 1;
        		}
        	}
        }
        
        previousAction = action;
        
        //... and return it.
        return actions[action];
    }

}
