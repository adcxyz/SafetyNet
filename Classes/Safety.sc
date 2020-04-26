Safety {

	classvar <all;
	classvar <synthDefFuncs;
	classvar <>defaultDefName = \safeClip;
	classvar <>useRootNode = true;
	classvar <classLimit = 1.0;

	var <server, <defName, <numChannels, <enabled;
	var <treeFunc, <synth;
	var <limit;

	*initClass {
		all = ();
		Class.initClassTree(Server);
		Class.initClassTree(SynthDescLib);
		this.initSynthDefFuncs;
		this.addServers;
	}

	*initSynthDefFuncs {
		synthDefFuncs = (
			\safeClip: { |numChans, limit = 1|
				{
					var limitCtl = \limit.kr(limit);
					var mainOuts = In.ar(0, numChans);
					var safeOuts = ReplaceBadValues.ar(mainOuts);
					var limited = safeOuts.clip2(limitCtl);
					ReplaceOut.ar(0, limited);
				}
			},
			\safeSoft: { |numChans, limit=1|
				{
					var limitCtl = \limit.kr(limit);
					var mainOuts = In.ar(0, numChans);
					var safeOuts = ReplaceBadValues.ar(mainOuts);
					var limited = safeOuts.softclip * limitCtl;
					ReplaceOut.ar(0, limited);
				}
			},
			\safeTanh: { |numChans, limit=1|
				{
					var limitCtl = \limit.kr(limit);
					var mainOuts = In.ar(0, numChans);
					var safeOuts = ReplaceBadValues.ar(mainOuts);
					var limited = safeOuts.tanh * limitCtl;
					ReplaceOut.ar(0, limited);
				}
			},
			\safeLimit: { |numChans, limit=1|
				{
					var limitCtl = \limit.kr(limit);
					var mainOuts = In.ar(0, numChans);
					var safeOuts = ReplaceBadValues.ar(mainOuts);
					var limited = Limiter.ar(safeOuts, limitCtl);
					ReplaceOut.ar(0, limited);
				}
			}
		);
	}

	*synthDefFor { |defName, numChans = 2|
		if (synthDefFuncs[defName].isNil) { ^nil };
		^SynthDef((defName ++ "_" ++ numChans).asSymbol, synthDefFuncs[defName].value(numChans));
	}

	*addSynthDefFunc { |defName, func|
		// dont .add def here, numChans may differ for each server
		synthDefFuncs.put(defName, func);
	}

	*addServers {
		// only adds new Safety objects for new servers
		Server.all.do { |server|
			Safety.all.put(server.name, Safety(server));
		};
	}

	*enable { all.do(_.enable) }
	*disable { all.do(_.disable) }

	*limit { ^classLimit }

	*setLimit { |val = 1.0|
		classLimit = val.clip(0, 1);
		all.do(_.setLimit(classLimit));
	}

	*new { |server, defName, enable = true, numChannels|
		if (all[server].notNil) { ^all[server] };
		if (all[server.name].notNil) { ^all[server.name] };
		^super.newCopyArgs(server, defName, numChannels).init;
	}

	storeArgs { ^[server.name] }
	printOn { |stream| ^this.storeOn(stream) }

	asTarget { ^if (useRootNode) { RootNode(server) } { server.defaultGroup } }

	init {
		treeFunc = {
			var numChans = numChannels ?? { server.options.numOutputBusChannels };
			var synDef = Safety.synthDefFor(defName ? defaultDefName, numChans);

			forkIfNeeded {
				server.makeBundle(nil, {
					if (synth.notNil) {
						server.sendMsg("/error", 0);
						synth.free;
						server.sendMsg("/error", 1);
					};
				});

				synDef.send(server);
				server.sync;
				synth = Synth.tail(this, synDef.name);
			};

			enabled = true;
			"% is running, using %.\n".postf(this, synDef.name.cs);
		};

		enabled = false;
		this.enable;
	}

	enable { |remake = false|

		if (remake) { this.disable };

		if (enabled) {
			"% enabled already.\n".postf(this);
			^this
		};

		ServerTree.add(treeFunc, server);
		if (server.serverRunning) { treeFunc.value };
		enabled  = true;
		"% enabled.\n".postf(this);
	}

	disable {
		synth.free;
		synth = nil;
		ServerTree.remove(treeFunc, server);
		enabled = false;
		"% disabled.\n".postf(this);
	}

	setLimit { |val|
		limit = val.clip(0, 1);
		if (synth.notNil) {
			synth.set(\limit, limit);
		}
	}

	numChannels_ { |num|
		if (numChannels == num) { ^this };
		if (num < 1) {
			"Safety: illegal numChannels %.\n".postf(num);
			^this
		};
		numChannels = num;
		if (enabled and: server.serverRunning) {
			this.enable(true);
		};
	}

	defName_ { |name|
		if (synthDefFuncs[name].isNil) {
			"%: no synthDef found for - keeping %.\n".postf(this, name.cs, defName);
			^this
		};
		defName = name;
		if (server.serverRunning) {
			this.enable(true);
		}
	}
}

