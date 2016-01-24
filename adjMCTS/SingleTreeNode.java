package controllers.adjMCTS;

import java.util.Random;

import javax.swing.plaf.nimbus.NimbusLookAndFeel;

import core.game.StateObservation;
import ontology.Types;
import ontology.Types.ACTIONS;
import tools.ElapsedCpuTimer;
import tools.Utils;

public class SingleTreeNode
{
    private static final double HUGE_NEGATIVE = -10000000.0;
    private static final double HUGE_POSITIVE =  10000000.0;
    public static double epsilon = 1e-6;
    public static double egreedyEpsilon = 0.05;
    public StateObservation state;
    public SingleTreeNode parent;
    public SingleTreeNode[] children;
    public double totValue;
    public double maxValue;
    public double bonusValue;
    public RowOdds repetition;
    public int nVisits;
    public static Random m_rnd;
    private int m_depth;
    protected static double[] bounds = new double[]{Double.MAX_VALUE, -Double.MAX_VALUE};
    public SingleTreeNode(Random rnd) {
        this(null, null, rnd, Agent.previousRepetition);
    }

    public SingleTreeNode(StateObservation state, SingleTreeNode parent, Random rnd, RowOdds previousRepetition) {
        this.state = state;
        this.parent = parent;
        this.m_rnd = rnd;
        children = new SingleTreeNode[Agent.NUM_ACTIONS];
        totValue = 0.0;
        maxValue = 0.0;
        bonusValue = 0.0;
        repetition = new RowOdds();
        repetition.actionHold = previousRepetition.actionHold;
        repetition.actionNewAction = previousRepetition.actionNewAction;
        repetition.actionNil = previousRepetition.actionNil;
        repetition.nilAction = previousRepetition.nilAction;
        repetition.nilHold = previousRepetition.nilHold;
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

    public SingleTreeNode expand() {
    	int bestAction = 0;
        double bestValue = -1;
    	
        bestAction = previousAction();
    	if(m_rnd.nextDouble() > Agent.expansionProb || bestAction < 0 || children[bestAction] != null){
	        for (int i = 0; i < children.length; i++) {
	            double x = m_rnd.nextDouble();
	            if (x > bestValue && children[i] == null) {
	                bestAction = i;
	                bestValue = x;
	            }
	        }
    	}

        StateObservation nextState = state.copy();
        nextState.advance(Agent.actions[bestAction]);

        SingleTreeNode tn = new SingleTreeNode(nextState, this, this.m_rnd, this.repetition);
        
        ACTIONS tempPrev = ACTIONS.ACTION_NIL;
        if(previousAction() >= 0){
        	tempPrev = Agent.actions[previousAction()];
        }
        if(tempPrev == ACTIONS.ACTION_NIL){
        	if(Agent.actions[bestAction] == ACTIONS.ACTION_NIL){
        		if((int)Math.round(tn.repetition.nilHold) < Agent.dataBonus.length){
        			tn.bonusValue = Agent.dataBonus[(int)Math.round(tn.repetition.nilHold)].nilHold;
    			}
        		tn.repetition.actionHold = 0;
        		tn.repetition.nilHold += 1;
        	}
        	else{
        		if((int)Math.round(tn.repetition.nilHold) < Agent.dataBonus.length){
        			tn.bonusValue = Agent.dataBonus[(int)Math.round(tn.repetition.nilHold)].nilAction;
    			}
        		tn.repetition.actionHold = 1;
        		tn.repetition.nilHold = 0;
        		tn.repetition.nilAction += 1;
        	}
        }
        else{
        	if(Agent.actions[bestAction] != tempPrev){
        		if(Agent.actions[bestAction] == ACTIONS.ACTION_NIL){
        			if((int)Math.round(tn.repetition.actionHold) < Agent.dataBonus.length){
        				tn.bonusValue = Agent.dataBonus[(int)Math.round(tn.repetition.actionHold)].actionNil;
        			}
        			tn.repetition.actionHold = 0;
        			tn.repetition.nilHold = 1;
        			tn.repetition.actionNil += 1;
        		}
        		else{
        			
        			if((int)Math.round(tn.repetition.actionHold) < Agent.dataBonus.length){
        				tn.bonusValue = Agent.dataBonus[(int)Math.round(tn.repetition.actionHold)].actionNewAction;
        			}
        			tn.repetition.actionHold = 1;
        			tn.repetition.nilHold = 0;
        			tn.repetition.actionNewAction += 1;
        		}
        	}
        	else{
        		if((int)Math.round(tn.repetition.actionHold) < Agent.dataBonus.length){
        			tn.bonusValue = Agent.dataBonus[(int)Math.round(tn.repetition.actionHold)].actionHold;
    			}
        		tn.repetition.actionHold += 1;
        		tn.repetition.nilHold = 0;
        	}
        }
        
        children[bestAction] = tn;
        return tn;
    }
    
    public double getRayleighCDF(double input){
    	return (1 - Math.exp(-Math.pow(input, 2) / (2 * Math.pow(Agent.sigmaRayleigh, 2))));
    }
    
    public SingleTreeNode uct() {

        SingleTreeNode selected = null;
        double bestValue = -Double.MAX_VALUE;
        for (SingleTreeNode child : this.children)
        {
            double hvVal = child.totValue;
            double childValue =  Agent.mixmax * maxValue + (1 - Agent.mixmax) * hvVal / (child.nVisits + this.epsilon);

            childValue = Utils.normalise(childValue, bounds[0], bounds[1]);

//            double uctValue = childValue +
//                    Agent.K * Math.sqrt(Math.log(this.nVisits + 1) / (child.nVisits + this.epsilon)) + 
//                    child.bonusValue * (1 - Agent.getActionBonus(child.actionLength));
            double uctValue = childValue +
                  Agent.K * Math.sqrt(Math.log(this.nVisits + 1) / (child.nVisits + this.epsilon)) + 
                  Agent.selectionBonus * child.bonusValue;
            
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
            if(m_rnd.nextDouble() < Agent.randomProb && 
            		currentAction != Types.ACTIONS.ACTION_NIL){
            	action = Agent.NUM_ACTIONS + 1;
            }
            
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
