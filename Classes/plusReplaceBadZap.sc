
+ UGen {
	zap { |sub = 0, id = 0, post = 0|
		if (rate == \audio) {
			^ReplaceBadValues.ar(this, sub, id, post);
		} {
			^ReplaceBadValues.kr(this, sub, id, post);
		}
	}
}
+ Array {
	zap { |sub = 0, id = 0, post = 0|
		sub = sub.asArray;
		^this.collect { |item, i|
			item.zap(sub.wrapAt(i), id, post);
		}
	}
}

