Safety {

	classvar <all;
	classvar <synthDefFuncs;
	classvar <>defaultDefName = \safeClip;
	classvar <>useRootNode = true;

	var <server, <defName, <treeFunc, <synth, <enabled = false;

	*initClass {
		all = ();
		Class.initClassTree(Server);
		Class.initClassTree(SynthDescLib);
		this.initSynthDefFuncs;
		this.addServers;
	}

	*initSynthDefFuncs {
		synthDefFuncs = (
			\safeClip: { |numChans|  { |limit=1|
				var mainOuts = In.ar(0, numChans);
				var safeOuts = ReplaceBadValues.ar(mainOuts);
				var limited = safeOuts.clip2(limit);
				ReplaceOut.ar(0, limited);
			} },
			\safeSoft: { |numChans| { |limit=1|
				var mainOuts = In.ar(0, numChans);
				var safeOuts = ReplaceBadValues.ar(mainOuts);
				var limited = safeOuts.softclip * limit;
				ReplaceOut.ar(0, limited);
			} },
			\safeTanh: { |numChans| { |limit=1|
				var mainOuts = In.ar(0, numChans);
				var safeOuts = ReplaceBadValues.ar(mainOuts);
				var limited = safeOuts.tanh * limit;
				ReplaceOut.ar(0, limited);
			} },
			\safeLimit: { |numChans| { |limit=1|
				var mainOuts = In.ar(0, numChans);
				var safeOuts = ReplaceBadValues.ar(mainOuts);
				var limited = Limiter.ar(safeOuts, limit);
				ReplaceOut.ar(0, limited);
			} }
		);
	}

	*synthDefFor { |name, numChans = 2|
		if (synthDefFuncs[name].isNil) { ^nil };
		^SynthDef(name, synthDefFuncs[name].value(numChans));
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


	*new { |server, defName = (defaultDefName), enable = false|
		if (all[server].notNil) { ^all[server] };
		if (all[server.name].notNil) { ^all[server.name] };
		^super.newCopyArgs(server, defName).init(true);
	}


	storeArgs { ^[server.name] }
	printOn { |stream| ^this.storeOn(stream) }

	numChannels { ^server.options.numOutputBusChannels }
	asTarget { ^if (useRootNode) { RootNode(server) } { server.defaultGroup } }

	init { |enable|
		treeFunc = {
			forkIfNeeded {
				server.makeBundle(nil, {
					if (synth.notNil) {
						server.sendMsg("/error", 0);
						synth.free;
						server.sendMsg("/error", 1);
					};
				});
				Safety.synthDefFor(defName).send(server);
				server.sync;
				synth = Synth.tail(this, defName);
				enabled = true;
				"% is running, using %.\n".postf(this, defName.cs);
			}
		};

		if (enable) { this.enable; };
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

	defName_ { |name|
		if (synthDefFuncs[name].isNil) {
			"%: no synthDef found for - keeping %.\n".postf(this, name.cs, defName);
			^this
		};
		defName = name;
	}
}

