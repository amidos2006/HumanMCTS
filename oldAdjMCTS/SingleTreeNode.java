package controllers.oldAdjMCTS;

import java.util.ArrayList;
import java.util.Random;

import core.game.Observation;
import core.game.StateObservation;
import ontology.Types;
import ontology.Types.ACTIONS;
import tools.ElapsedCpuTimer;
import tools.Utils;
import tools.Vector2d;

public class SingleTreeNode
{
    private static final double HUGE_NEGATIVE = -10000000.0;
    private static final double HUGE_POSITIVE =  10000000.0;
    private static final SingleTreeNode unexpanded = new SingleTreeNode(null);
    public static double epsilon = 1e-6;
    public static double egreedyEpsilon = 0.05;
    public StateObservation state;
    public SingleTreeNode parent;
    public SingleTreeNode[] children;
    public double totValue;
    public double maxValue;
    public double bonusValue;
    public double penaltyValue;
    public int actionRepeat;
    public int nilRepeat;
    public int nVisits;
    public static Random m_rnd;
    private int m_depth;
    protected static double[] bounds = new double[]{Double.MAX_VALUE, -Double.MAX_VALUE};
    public SingleTreeNode(Random rnd) {
        this(null, null, rnd, Agent.actionRepeat, Agent.nilRepeat);
    }

    public SingleTreeNode(StateObservation state, SingleTreeNode parent, Random rnd, int actRepeat, int nilRepeat) {
        this.state = state;
        this.parent = parent;
        this.m_rnd = rnd;
        children = new SingleTreeNode[Agent.NUM_ACTIONS];
        totValue = 0.0;
        maxValue = 0.0;
        bonusValue = 0.0;
        penaltyValue = 0.0;
        this.actionRepeat = actRepeat;
        this.nilRepeat = nilRepeat;
        if(parent != null)
            m_depth = parent.m_depth+1;
        else
            m_depth = 0;
    }


    public void mctsSearch(ElapsedCpuTimer elapsedTimer) {
        double avgTimeTaken = 0;
        double acumTimeTaken = 0;
        long remaining = elapsedTimer.remainingTimeMillis();
        int numIters = 0;

        int remainingLimit = 5;
        while(remaining > 2*avgTimeTaken && remaining > remainingLimit){
            ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();
            SingleTreeNode selected = treePolicy();
            double delta = selected.rollOut();
            backUp(selected, delta);

            numIters++;
            acumTimeTaken += (elapsedTimerIteration.elapsedMillis()) ;

            avgTimeTaken  = acumTimeTaken/numIters;
            remaining = elapsedTimer.remainingTimeMillis();
            //System.out.println(elapsedTimerIteration.elapsedMillis() + " --> " + acumTimeTaken + " (" + remaining + ")");
        }
        //System.out.println("-- " + numIters + " -- ( " + avgTimeTaken + ")");
    }

    public SingleTreeNode treePolicy() {

        SingleTreeNode cur = this;

        while (!cur.state.isGameOver() && cur.m_depth < Agent.ROLLOUT_DEPTH)
        {
            if (cur.notFullyExpanded()) {
                return cur.expand();

            } else {
                SingleTreeNode next = cur.uct();
                //SingleTreeNode next = cur.egreedy();
                cur = next;
            }
        }

        return cur;
    }
    
    private int previousAction(){
    	if(this.parent == null)
    		return Agent.previousAction;
    	
    	if(Agent.wholeTree){
	    	for(int i=0; i<this.parent.children.length; i++){
	    		if(this.parent.children[i] == this){
	    			return i;
	    		}
	    	}
    	}
    	
    	return -1;
    }

    private boolean isDestructed(ArrayList<Observation>[] oldPos, ArrayList<Observation>[] newPos, int shootingType){
		if((oldPos == null && newPos != null) || (oldPos == null && newPos == null)){
			return false;
		}
		if(oldPos != null && newPos == null){
			for(int i=0; i<oldPos.length; i++){
				for(Observation o:oldPos[i]){
					if(o.itype != shootingType){
						return true;
					}
				}
			}
			return false;
		}
		
		for(int i=0; i<oldPos.length; i++){
			if(i >= newPos.length || oldPos[i].size() > newPos[i].size()){
				for(Observation o:oldPos[i]){
					if(o.itype != shootingType){
						return true;
					}
				}
			}
		}
		return false;
	}
    
    private boolean checkObjectChange(StateObservation before, StateObservation after){
    	ArrayList<Observation>[] oldPos = before.getImmovablePositions();
		ArrayList<Observation>[] newPos = after.getImmovablePositions();
		int shootingType = -1;
		ArrayList<Observation>[] bullets = after.getFromAvatarSpritesPositions();
		if(bullets.length > 0){
			for(int i=0; i<bullets.length; i++){
				for(Observation o:bullets[i]){
					shootingType = o.itype;
					break;
				}
				if(shootingType != -1){
					break;
				}
			}
		}
		if(isDestructed(oldPos, newPos, shootingType)){
			return true;
		}
		
    	oldPos = before.getMovablePositions();
		newPos = after.getMovablePositions();
		if(isDestructed(oldPos, newPos, shootingType)){
			return true;
		}
		
		oldPos = before.getNPCPositions();
		newPos = after.getNPCPositions();
		if(isDestructed(oldPos, newPos, shootingType)){
			return true;
		}
		
		oldPos = before.getPortalsPositions();
		newPos = after.getPortalsPositions();
		if(isDestructed(oldPos, newPos, shootingType)){
			return true;
		}
		
		oldPos = before.getResourcesPositions();
		newPos = after.getResourcesPositions();
		if(isDestructed(oldPos, newPos, shootingType)){
			return true;
		}
		
	    return false;
    }
    
    private boolean checkObjectFront(StateObservation state){
    	Vector2d infrontOfPlayer = state.getAvatarPosition().mul(1.0 / state.getBlockSize()).add(state.getAvatarOrientation());
    	return state.getObservationGrid()[(int)infrontOfPlayer.x][(int)infrontOfPlayer.y].size() > 0;
    }
    
    public SingleTreeNode expand() {
    	int bestAction = 0;
        double bestValue = -1;
    	
        ACTIONS tempPrev = ACTIONS.ACTION_NIL;
        if(previousAction() >= 0){
        	tempPrev = Agent.actions[previousAction()];
        }
        
        bestAction = previousAction();
    	if(m_rnd.nextDouble() > Agent.expansionProb || bestAction < 0 || children[bestAction] != null){
	        for (int i = 0; i < children.length; i++) {
	            double x = m_rnd.nextDouble();
	            if (x > bestValue && children[i] == null) {
	            	StateObservation tempState = state.copy();
	            	tempState.advance(Agent.actions[i]);
	            	if(Agent.actions[i] == ACTIONS.ACTION_USE && 
	            			checkObjectFront(state) && !checkObjectChange(state, tempState)){
	            		children[i] = unexpanded;
	            		continue;
	            	}
	            	if(ACTIONS.isMoving(Agent.actions[i]) && 
	            			state.getAvatarPosition().equals(tempState.getAvatarPosition()) && 
	            			state.getAvatarOrientation().equals(tempState.getAvatarOrientation())){
	            		children[i] = unexpanded;
	            		continue;
	            	}
	                bestAction = i;
	                bestValue = x;
	            }
	        }
    	}

        StateObservation nextState = state.copy();
        if(bestAction < 0){
        	for(int i=0; i < Agent.actions.length; i++){
        		if(Agent.actions[i] == ACTIONS.ACTION_NIL){
        			bestAction = i;
        			break;
        		}
        	}
        }
        nextState.advance(Agent.actions[bestAction]);

        SingleTreeNode tn = new SingleTreeNode(nextState, this, this.m_rnd, this.actionRepeat, this.nilRepeat);
        
        if(tempPrev == ACTIONS.ACTION_NIL){
        	if(tempPrev == Agent.actions[bestAction]){
        		tn.bonusValue = Agent.higherNIL * Agent.getBonus(Agent.nilBonus, tn.nilRepeat);
        		if(tn.nilRepeat > 1)
        		System.out.println("NIL->NIL: " + tn.nilRepeat + " " + tn.bonusValue);
        		tn.nilRepeat += 1;
        	}
        	else{
        		tn.bonusValue = 1 - Agent.getBonus(Agent.nilBonus, tn.nilRepeat);
        		if(tn.nilRepeat > 1)
        		System.out.println("NIL->ACTION: " + tn.nilRepeat + " " + tn.bonusValue);
        		tn.nilRepeat = 0;
        		tn.actionRepeat = 1;
        	}
        }
        else{
        	if(tempPrev == Agent.actions[bestAction]){
        		tn.bonusValue = Agent.getBonus(Agent.actionBonus, tn.actionRepeat);
        		if(tn.actionRepeat > 1)
        		System.out.println("Action->ACTION: " + tn.actionRepeat + " " + tn.bonusValue);
        		tn.actionRepeat += 1;
        	}
        	else if(Agent.actions[bestAction] == ACTIONS.ACTION_NIL){
        		tn.bonusValue = 0.9 * ( 1- Agent.getBonus(Agent.actionBonus, tn.actionRepeat));
        		if(tn.actionRepeat > 1)
        		System.out.println("Action->NIL: " + tn.actionRepeat + " " + tn.bonusValue);
        		tn.nilRepeat = 1;
        		tn.actionRepeat = 0;
        	}
        	else{
        		if(Agent.actions[bestAction] != ACTIONS.reverseACTION(tempPrev)){
        			tn.bonusValue = 0.1 * (1 - Agent.getBonus(Agent.actionBonus, tn.actionRepeat));
        		}
        		if(tn.actionRepeat > 1)
        		System.out.println("Action->NewAction: " + tn.actionRepeat + " " + tn.bonusValue);
        		tn.actionRepeat = 1;
        	}
        }
        
        children[bestAction] = tn;
        return tn;
    }
    
    public SingleTreeNode uct() {

        SingleTreeNode selected = null;
        double bestValue = -Double.MAX_VALUE;
        for (SingleTreeNode child : this.children)
        {
        	if(child == unexpanded){
        		System.out.println("------------------Skip Stupid Node------------------");
        		continue;
        	}
            double hvVal = child.totValue;
            double mxVal = child.maxValue;
            if(mxVal > Agent.enlargeMaxValue){
            	mxVal *= Agent.enlargeMaxValue;
            }
            double childValue =  Agent.mixmax * mxVal + (1 - Agent.mixmax) * hvVal / (child.nVisits + this.epsilon);
            Vector2d playerPos = child.state.getAvatarPosition().mul(1.0 / child.state.getBlockSize());
            double exploreScore = (Agent.maxVisited - Agent.visited[(int)playerPos.x][(int)playerPos.y]) / 
            		(Agent.maxVisited);
            childValue = Utils.normalise(childValue, bounds[0], bounds[1]);

            double uctValue = childValue +
                  Agent.K * Math.sqrt(Math.log(this.nVisits + 1) / (child.nVisits + this.epsilon)) + 
                  Agent.selectionBonus * child.bonusValue + Agent.exploreFraction * exploreScore;
            
            // small sampleRandom numbers: break ties in unexpanded nodes
            uctValue = Utils.noise(uctValue, this.epsilon, this.m_rnd.nextDouble());     //break ties randomly

            // small sampleRandom numbers: break ties in unexpanded nodes
            if (uctValue > bestValue) {
                selected = child;
                bestValue = uctValue;
            }
        }

        if (selected == null)
        {
            throw new RuntimeException("Warning! returning null: " + bestValue + " : " + this.children.length);
        }

        return selected;
    }

    public SingleTreeNode egreedy() {
        SingleTreeNode selected = null;

        if(m_rnd.nextDouble() < egreedyEpsilon)
        {
            //Choose randomly
            int selectedIdx = m_rnd.nextInt(children.length);
            selected = this.children[selectedIdx];

        }else{
            //pick the best Q.
            double bestValue = -Double.MAX_VALUE;
            for (SingleTreeNode child : this.children)
            {
                double hvVal = child.totValue;
                hvVal = Utils.noise(hvVal, this.epsilon, this.m_rnd.nextDouble());     //break ties randomly
                // small sampleRandom numbers: break ties in unexpanded nodes
                if (hvVal > bestValue) {
                    selected = child;
                    bestValue = hvVal;
                }
            }

        }

        if (selected == null)
        {
            throw new RuntimeException("Warning! returning null: " + this.children.length);
        }

        return selected;
    }


    public double rollOut()
    {
        StateObservation rollerState = state.copy();
        int thisDepth = this.m_depth;

        Types.ACTIONS currentAction = Types.ACTIONS.ACTION_NIL;
        if(previousAction() >= 0){
        	currentAction = Agent.actions[previousAction()];
        }
        while (!finishRollout(rollerState,thisDepth)) {
        	
            int action = m_rnd.nextInt(Agent.NUM_ACTIONS);
            if(action < Agent.actions.length){
            	currentAction = Agent.actions[action];
            }
            rollerState.advance(currentAction);
            thisDepth++;
        }

        double delta = value(rollerState);

        if(delta < bounds[0])
            bounds[0] = delta;

        if(delta > bounds[1])
            bounds[1] = delta;

        return delta;
    }
    
    public double value(StateObservation a_gameState) {

        boolean gameOver = a_gameState.isGameOver();
        Types.WINNER win = a_gameState.getGameWinner();
        double rawScore = a_gameState.getGameScore();

        if(gameOver && win == Types.WINNER.PLAYER_LOSES)
            rawScore += HUGE_NEGATIVE;

        if(gameOver && win == Types.WINNER.PLAYER_WINS)
            rawScore += HUGE_POSITIVE;
        
        return rawScore;
    }

    public boolean finishRollout(StateObservation rollerState, int depth)
    {
        if(depth >= Agent.ROLLOUT_DEPTH)      //rollout end condition.
            return true;

        if(rollerState.isGameOver())               //end of game
            return true;

        return false;
    }

    public void backUp(SingleTreeNode node, double result)
    {
        SingleTreeNode n = node;
        while(n != null)
        {
            n.nVisits++;
            if(result > maxValue){
            	result = maxValue;
            }
            n.totValue += result;
            n = n.parent;
        }
    }


    public int mostVisitedAction() {
        int selected = -1;
        double bestValue = -Double.MAX_VALUE;
        boolean allEqual = true;
        double first = -1;

        for (int i=0; i<children.length; i++) {

            if(children[i] != null)
            {
                if(first == -1)
                    first = children[i].nVisits;
                else if(first != children[i].nVisits)
                {
                    allEqual = false;
                }

                double childValue = children[i].nVisits;
                childValue = Utils.noise(childValue, this.epsilon, this.m_rnd.nextDouble());     //break ties randomly
                if (childValue > bestValue) {
                    bestValue = childValue;
                    selected = i;
                }
            }
        }

        if (selected == -1)
        {
            System.out.println("Unexpected selection!");
            selected = 0;
        }else if(allEqual)
        {
            //If all are equal, we opt to choose for the one with the best Q.
            selected = bestAction();
        }
        return selected;
    }

    public int bestAction()
    {
        int selected = -1;
        double bestValue = -Double.MAX_VALUE;

        for (int i=0; i<children.length; i++) {

            if(children[i] != null) {
                double childValue = children[i].totValue / (children[i].nVisits + this.epsilon);
                childValue = Utils.noise(childValue, this.epsilon, this.m_rnd.nextDouble());     //break ties randomly
                if (childValue > bestValue) {
                    bestValue = childValue;
                    selected = i;
                }
            }
        }

        if (selected == -1)
        {
            System.out.println("Unexpected selection!");
            selected = 0;
        }

        return selected;
    }


    public boolean notFullyExpanded() {
        for (SingleTreeNode tn : children) {
            if (tn == null) {
                return true;
            }
        }

        return false;
    }
}
