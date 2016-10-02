package com.github.monster860.fastdmm.editing;

/**
 * A general interface for actions that are undo and redoable.
 */
public abstract class Action {

	/**
	 * Called to undo an action.
	 */
	public abstract void undo();

	/**
	 * Called to redo an action.
	 */
	public void redo() {
		apply();
	}

	/**
	 * Called to apply an action initially.
	 */
	public abstract void apply();
}
