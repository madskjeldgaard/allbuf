/*

@TODO:
- instead of booleans, a "mode" system


*/
AllBuf { 
	var <inChannels, <outChannels, <modes, mode;

	*new {|maxinchans=2, outchans=2, verbose=true|
			^super.new.init(maxinchans, outchans, verbose)
	}

	init{|maxchansIn, outchans, verbose|
		modes = [\normal, \pitch, \filter, \novca];
		inChannels = maxchansIn ? 2;
		outChannels = outchans ? 2;

		(1..maxchansIn).do{|inchan|

			modes.do{|m|
				this.addSynth(inchan, outchans, mode: m, verbose: verbose);
			}
		};

	}

	def{|inchans=1, mode| 
		var basename, suffix;

		if(modes.indexOfEqual(mode).isNil, {
			"AllBuf mode % does not exist".format(mode).error;
		}, {
			if(inchans > inChannels, {
				"Cannot return synthdef with % in channels.\nAllBuf was compiled with % in channels".format(inchans, inChannels).warn;
				inchans = inChannels
			});

			basename = "allbuf_%i_%o_%".format(inchans, outChannels, mode);

		});

		^basename.asSymbol
	}

	addSynth{
		arg inchans=2,
			outchans=2,
			mode,
			verbose=false;

		var pitchEnv, lpf, filterEnv, vca;
		var name, synthfunc;

		verbose.if({ 
			"Creating AllBuf synth for mode %".format(mode).postln 
		});

		// This is where the synthdef configuration is done
		// @TODO rewrite this to use dicts for configurations instead
		case 
		{  mode == \normal } {  
			pitchEnv=true; 
			lpf=true; 
			filterEnv=true; 
			vca=true;
		}
		{  mode == \pitch } {
			pitchEnv=true; 
			lpf=false; 
			filterEnv=false; 
			vca=true;
		}
		{  mode == \filter } {
			pitchEnv=false; 
			lpf=true; 
			filterEnv=true; 
			vca=true;
		}
		{ mode == \novca } {
			pitchEnv=false; 
			lpf=false; 
			filterEnv=false; 
			vca=false;
		};

		name = this.def(
			inchans: inchans, 
			mode: mode
		);

		synthfunc = this.synthFunc(
			inchans: inchans, 
			outchans: outchans, 
			lpf: lpf, 
			filterEnv: filterEnv, 
			pitchEnv: pitchEnv,
			vca: vca
		);

		verbose.if({
			"Making SynthDef '%'".format(name).postln;
			"inchans: %, outchans: %".format(inchans,outchans).postln;
			"Mode: %".format(mode).postln;
		});

		// Make and add the SynthDef
		SynthDef.new(name.postln, synthfunc).add;

		verbose.if({
			this.postArguments(inchans, mode);
			"----------".postln;
		})
	}

	synthFunc{|inchans=1, outchans=2, lpf=true, filterEnv=true, pitchEnv=true, vca=false|
		var func = if(vca.not, {
			// Simple, non enveloped synth
			// Lifetime detertmined by the PlayBuf doneaction
			{|dur=1, amp=0.5, out=0|
				var sig = SynthDef.wrap(this.bufPlayerFunc(inchans: inchans, pitchEnv: false), prependArgs: []);

				sig = SynthDef.wrap(this.panFunc(inchans: inchans, outchans:outchans), prependArgs:[sig]);

				Out.ar(out, sig * amp);
			}
		}, {
			// enveloped synth
			{|dur=1, amp=0.5, out=0|

				var env = SynthDef.wrap(this.envFunc(), prependArgs: [dur]); 
				var sig = SynthDef.wrap(this.bufPlayerFunc(inchans: inchans, pitchEnv: pitchEnv), prependArgs: [env]);

				sig = SynthDef.wrap(this.panFunc(inchans: inchans, outchans:outchans), prependArgs:[sig]);
				sig = SynthDef.wrap(this.filterFunc(lpf: lpf, filterEnv: filterEnv),  prependArgs: [sig, env]);

				Out.ar(out, env * sig * amp);
			}
		});

		^func
	}	

	panFunc{|inchans=1, outchans=2|
		var panfunc = case
		{(inchans == 1).and(outchans == 1)} { 
			{|sig| sig }
		}
		{(inchans == 1).and( outchans == 2 )} { 
			{|sig, pan=0, panFreq=0.1, autopan=0, panShape=(-1.0)| 
				var panner = AutoPan.kr(pan, panFreq, autopan, panShape);
				Pan2.ar(sig, panner) 
			}
		}
		{(inchans == 1).and(outchans > 2)} {
			{|sig, width=1.0, orientation=0.5, pan=0, panFreq=0.1, autopan=0, panShape=(-1.0)| 
				var panner = AutoPan.kr(pan, panFreq, autopan, panShape);
 
				PanAz.ar(
					numChans: outchans, 
					in: sig, 
					pos: panner,
					width: width, 
					orientation: orientation
				) 
			}
		}
		// Stereo in
		{(inchans >= 2).and(outchans == 1)} {
			{|sig| 
				Mix.ar(sig) // TODO: SelectXFocus ?
			}
		}
		// {(inchans >= 2).and(outchans == 1)} {
		// 	"Summing signal (no panning)".postln;
		// 	{|sig, pan=0.5, focus=1, wrap=false| 
		// 		SelectXFocus.ar(pan,  array: sig,  focus: focus,  wrap: wrap)
		// 	}
		// }
		{(inchans == 2).and(outchans == 2)} {
			{|sig, pan=0, panFreq=0.1, autopan=0, panShape=(-1.0)| 
				var panner = AutoPan.kr(pan, panFreq, autopan, panShape);
				Balance2.ar(sig[0], sig[1], pos: panner) 
			}
		}
		{(inchans > 2).and(outchans == 2)} {
			{|sig, spread=1, width=1, orientation=0.5, pan=0, panFreq=0.1, autopan=0, panShape=(-1.0)| 
				var panner = AutoPan.kr(pan, panFreq, autopan, panShape);
				Splay.ar(
					sig,  
					spread: spread,  
					level: 1,  
					center: panner,  
					levelComp: true
				)			
			}
		}
		// Stereo and multi chanin multi chan out
		{(inchans >= 2).and(outchans > 2)} {
			{|sig, spread=1, width=1, orientation=0.5, pan=0, panFreq=0.1, autopan=0, panShape=(-1.0)| 
				var panner = AutoPan.kr(pan, panFreq, autopan, panShape);

				SplayAz.ar(
					outchans, 
					sig,  
					spread: spread,  
					level: 1,  
					width: width,  
					center: panner,  
					orientation: orientation,  
					levelComp: true
				)
			}
		};

		^panfunc
	}

	envFunc{
		var envfunc = {|dur=1, attack=0.1, sustain=1, release=0.9, curve=4|
			EnvGen.kr(
				Env.perc(
					attack, 
					release, 
					1, 
					curve
				),  
				gate: 1.0,  
				levelBias: 0.0, 
				timeScale: dur * sustain,  
				doneAction: 2
			)
		};

		^envfunc
	}

	filterFunc{|lpf=true, filterEnv=true|
		var filterfunc = if(lpf, 
			{
				if(filterEnv,
					{

						{|in, env, cutoff=20000.0, resonance=0.5, filterenv=0.5|
							// Lag added to filter envelope to seperate it from amplitude envelope
							var fenv = env.lag2.range(1-filterenv*cutoff, cutoff).clip(20.0,20000.0);

							DFM1.ar(in, fenv,  resonance,  noiselevel: 0.0)
						}

					},
					{
						{|in, env, cutoff=20000, resonance=0.5|
							DFM1.ar(in, cutoff.clip(20.0,20000.0),  resonance,  noiselevel: 0.0)
						}
					}
				);

			}, {
				{|in| in }
			}
		);

		^filterfunc
	}

	bufPlayerFunc{|inchans, pitchEnv=true|
		var bufplayerfunc = if(pitchEnv, 
			{
				{|env, buffer, rate=1, trigger=1, start=0, loop=1, pitchenv=0.5, doneAction=0|
					// Lag added to pitch envelope to seperate it from amplitude envelope
					var penv = env.lag.range(1-pitchenv*rate, rate);

					PlayBuf.ar(
						inchans, 
						buffer, 
						penv * BufRateScale.kr(buffer),  
						trigger,  
						start * BufFrames.kr(buffer),  
						loop,
						doneAction: doneAction
					)
				}
			},
			{
				{|env, buffer, rate=1, trigger=1, start=0, loop=1, doneAction=0|
					PlayBuf.ar(
						inchans, 
						buffer, 
						rate * BufRateScale.kr(buffer),  
						trigger,  
						start * BufFrames.kr(buffer),  
						loop,
						doneAction: doneAction
					)
				}
			}
		);

		^bufplayerfunc
	}

	postArguments{|inchans=1, mode|
		"SynthDef % has the following control keys:".format(this.def(inchans, mode)).postln;

		this.getControlDict(inchans, mode).keysValuesDo{|key, ctrl|
			"\t\\".post;
			key.post;
			", ".post;
			ctrl.defaultValue.postln
		}
	}

	getControls{|inchans=1, mode| 
		^SynthDescLib.global.at(this.def(inchans, mode)).controls
	}

	getControlDict{|inchans=1, mode| 
		^SynthDescLib.global.at(this.def(inchans, mode)).controlDict
	}

	getControlNames{|inchans=1, mode| 
		^SynthDescLib.global.at(this.def(inchans, mode)).controlNames
	}

	// @TODO the defs still don't have specs
	getSpecs{|inchans=1, mode| 
		^SynthDescLib.global.at(this.def(inchans, mode)).specs
	}

}
