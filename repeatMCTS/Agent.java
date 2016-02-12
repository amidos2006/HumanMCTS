package controllers.repeatMCTS;

import java.util.ArrayList;
import java.util.Random;

import core.game.StateObservation;
import core.player.AbstractPlayer;
import ontology.Types.ACTIONS;
import ontology.Types.WINNER;
import tools.ElapsedCpuTimer;
import tools.IO;

public class Agent extends AbstractPlayer{
	
	public static double lambda = 5;
	public static double sigma = 2;
	public static double surpriseProb = 1;
	public static double nonMoveProb = 1;
	
	private final int DECIDE_ACTION = 0;
	private final int REPEAT_MOVE = 1;
	private final int REPEAT_NIL = 2;
	
	private ACTIONS pastAction;
	private double moves;
	private double nilMoves;
	private int currentState;
	private AbstractPlayer automatedPlayer;
	private Random random;
	private ArrayList<Double> actDist;
	private ArrayList<Double> nilDist;
	
	public Agent(StateObservation stateObs, ElapsedCpuTimer elapsedTimer){
		moves = 0;
		nilMoves = 0;
		pastAction = ACTIONS.ACTION_NIL;
		currentState = DECIDE_ACTION;
		controllers.sampleMCTS.Agent.NIL_INCLUDED = false;
		automatedPlayer = new controllers.sampleMCTS.Agent(stateObs, elapsedTimer);
		random = new Random();
		
		actDist = new ArrayList<Double>();
        nilDist = new ArrayList<Double>();
        
        IO reader = new IO();
        String[] values = reader.readFile("action.txt")[0].split(",");
        for (String v:values){
        	actDist.add(Double.parseDouble(v.trim()));
        }
        
        values = reader.readFile("nil.txt")[0].split(",");
        for (String v:values){
        	nilDist.add(Double.parseDouble(v.trim()));
        }
	}
	
	private ArrayList<Double> getCDF(ArrayList<Double> dist){
		ArrayList<Double> array = new ArrayList<Double>();
		
		array.add(dist.get(0));
		for(int i=1; i<dist.size(); i++){
			array.add(array.get(i - 1) + dist.get(i));
		}
		return array;
	}
	
	private int getNextEmpericalDist(ArrayList<Double> dist){
		ArrayList<Double> cdf = getCDF(dist);
		double value = random.nextDouble();
		for(int i=0; i<cdf.size(); i++){
			if(value < cdf.get(i)){
				return i;
			}
		}
		return dist.size();
	}
	
	@Override
	public ACTIONS act(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {
		ACTIONS currentAction = ACTIONS.ACTION_NIL;
		
		if(random.nextDouble() < surpriseProb){
			StateObservation tempState = stateObs.copy();
			tempState.advance(pastAction);
			if(tempState.getGameWinner() == WINNER.PLAYER_LOSES){
				moves = 0;
				nilMoves = 0;
				currentState = DECIDE_ACTION;
			}
		}
		
		if(random.nextDouble() < nonMoveProb && 
				pastAction != ACTIONS.ACTION_USE && pastAction != ACTIONS.ACTION_NIL){
			StateObservation tempState = stateObs.copy();
			tempState.advance(pastAction);
			if(tempState.getAvatarPosition().equals(stateObs.getAvatarPosition()) &&
				tempState.getAvatarOrientation().equals(stateObs.getAvatarOrientation())){
				moves = 0;
				nilMoves = 0;
				currentState = DECIDE_ACTION;
			}
		}
		
		switch(currentState){
		case DECIDE_ACTION:
			int temp = getNextEmpericalDist(nilDist);
			
			if(pastAction == ACTIONS.ACTION_NIL || 
					(pastAction != ACTIONS.ACTION_NIL && temp == 0)){
				currentAction = automatedPlayer.act(stateObs, elapsedTimer);
				moves = getNextEmpericalDist(actDist);
				if(moves > 1){
					currentState = REPEAT_MOVE;
				}
			}
			else{
				automatedPlayer.act(stateObs, elapsedTimer);
				currentAction = ACTIONS.ACTION_NIL;
				nilMoves = temp;
				if(temp > 1){
					currentState = REPEAT_NIL;
				}
			}
			break;
		case REPEAT_MOVE:
			automatedPlayer.act(stateObs, elapsedTimer);
			currentAction = pastAction;
			if(moves >= 1){
				moves -= 1;
			}
			else{
				currentState = DECIDE_ACTION;
			}
			break;
		case REPEAT_NIL:
			automatedPlayer.act(stateObs, elapsedTimer);
			currentAction = ACTIONS.ACTION_NIL;
			if(nilMoves >= 1){
				nilMoves -= 1;
			}
			else{
				currentState = DECIDE_ACTION;
			}
			break;
		}
		
		pastAction = currentAction;
		
		return currentAction;
	}

}
