package edu.washington.cse.instrumentation.runtime;

public enum CheckLevel {
	LINEAR {
		@Override
		public CheckLevel merge(CheckLevel checkLevel) {
			return checkLevel;
		}
	},
	STRICT {
		@Override
		public CheckLevel merge(CheckLevel checkLevel) {
			if(checkLevel == LINEAR || checkLevel == this) {
				return this;
			} else { return checkLevel; }
		}
	},
	TRANSACT {
		@Override
		public CheckLevel merge(CheckLevel checkLevel) {
			if(checkLevel == NONE) {
				return checkLevel;
			} else {
				return this;
			}
		}
	},
	NONE {
		@Override
		public CheckLevel merge(CheckLevel checkLevel) {
			return this;
		}
	};
	/* Dummy, no implementation */
	public CheckLevel merge(CheckLevel checkLevel) {
		return null;
	}
}
