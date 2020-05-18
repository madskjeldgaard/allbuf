AllBuf { 
	var <result, <inChannels, <outChannels;

	*new {|maxinchans=2, outchans=2, verbose=true|
			^super.new.init(maxinchans, outchans, verbose)
	}

	init{|outchans, maxchansIn, verbose|
		inChannels = maxchansIn ? 2;
		outChannels = outchans ? 2;

		(1..maxchansIn).do{|inchan|

			// No added goodies
			this.addSynth(inchan, outchans, pitchEnv: false, lpf: false, filterEnv: false, verbose: verbose);

			// With pitch env
			this.addSynth(inchan, outchans, pitchEnv: true, lpf: false, filterEnv: false, verbose: verbose);

			// With filter and filter env
			this.addSynth(inchan, outchans, pitchEnv: false, lpf: true, filterEnv: true, verbose: verbose);

			// With pitch and filter and filter env
			this.addSynth(inchan, outchans, pitchEnv: true, lpf: true, filterEnv: true, verbose: verbose);

		};

		result.postln;
	}

	name{|inchans=1, outchans=2, filterenv=true, pitchenv=true| 
		var basename = "allbuf_%i_%o".format(inchans, outchans);

		if(filterenv, { basename = basename ++ "_fenv" });
		if(pitchenv, { basename = basename ++ "_penv" });

		^basename.asSymbol

	}

	addSynth{
		arg inchans=2,
			outchans=2,
			pitchEnv = true,
			lpf=true,
			filterEnv=true,
			verbose=false;

		var name = this.getNameFor(
			inchans: inchans, 
			outchans: outchans, 
			filterenv: filterEnv, 
			pitchenv: pitchEnv
		);

		var synthfunc = this.synthFunc(
			inchans: inchans, 
			outchans: outchans, 
			lpf: lpf, 
			filterEnv: filterEnv, 
			pitchEnv: pitchEnv
		);

		verbose.if({
			"Making SynthDef '%'".format(name).postln;
			"inchans: %, outchans: %".format(inchans,outchans).postln;
			"----------".postln;
		});

		// Make and add the SynthDef
		SynthDef.new(name, synthfunc).add;
	}

	addToLog{|string|
		result = result ++ "\n" ++ string;

		^result
	}

	synthFunc{|inchans=1, outchans=2, lpf=true, filterEnv=true, pitchEnv=true|
		var func = {|dur=1, amp=1, out=0|

			var env = SynthDef.wrap(this.envFunc(), prependArgs: [dur]); 
			var sig = SynthDef.wrap(this.bufPlayerFunc(inchans: inchans, pitchEnv: pitchEnv), prependArgs: [env]);

			sig = SynthDef.wrap(this.panFunc(inchans: inchans, outchans:outchans), prependArgs:[sig]);
			sig = SynthDef.wrap(this.filterFunc(lpf: lpf, filterEnv: filterEnv),  prependArgs: [sig, env]);

			Out.ar(out, env * sig * amp);
		};

		^func
	}	

	panFunc{|inchans=1, outchans=2|
		var panfunc = case
		{(inchans == 1).and(outchans == 1)} { 
			this.addToLog("Setting pan function to mono (no panning)");
			{|sig| sig }
		}
		{(inchans == 1).and( outchans == 2 )} { 
			this.addToLog("Setting pan function to mono to stereo ");
			{|sig, pan| 
				Pan2.ar(sig, pan) 
			}
		}
		{(inchans == 1).and(outchans > 2)} {
			this.addToLog("Setting pan function to multichan (% channels)".format(outchans));
			{|sig, pan=0.0, width=1.0, orientation=0.5| 
				PanAz.ar(
					numChans: outchans, 
					in: sig, 
					pos: pan,
					width: width, 
					orientation: orientation
				) 
			}
		}
		// Stereo in
		{(inchans >= 2).and(outchans == 1)} {
			this.addToLog("Summing signal (no panning)");
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
			this.addToLog("Setting pan function to stereo");
			{|sig, pan=0.5| 
				Balance2.ar(sig[0], sig[1], pos: pan) 
			}
		}
		{(inchans > 2).and(outchans == 2)} {
			this.addToLog("Setting pan function to splay");
			{|sig, pan=0.5, spread=1, width=1, orientation=0.5| 
				Splay.ar(
					sig,  
					spread: spread,  
					level: 1,  
					center: pan,  
					levelComp: true
				)			
			}
		}
		// Stereo and multi chanin multi chan out
		{(inchans >= 2).and(outchans > 2)} {
			this.addToLog("Setting pan function to splay");
			{|sig, pan=0.5, spread=1, width=1, orientation=0.5| 
				SplayAz.ar(
					outchans, 
					sig,  
					spread: spread,  
					level: 1,  
					width: width,  
					center: pan,  
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
						this.addToLog("Adding Low pass filter with envelope");

						{|in, env, cutoff=20000.0, resonance=0.5, filterenv=0.5|
							// Lag added to filter envelope to seperate it from amplitude envelope
							var filterenv = env.lag2.range(1-filterenv*cutoff, cutoff).clip(20.0,20000.0);

							DFM1.ar(in, filterenv,  resonance,  noiselevel: 0.0)
						}

					},
					{
						this.addToLog("Adding Low pass filter");
						{|in, env, cutoff=20000, resonance=0.5|
							DFM1.ar(in, cutoff.clip(20.0,20000.0),  resonance,  noiselevel: 0.0)
						}
					}
				);

			}, {
				this.addToLog("Not adding Low pass filter");
				{|in| in }
			}
		);

		^filterfunc
	}

	bufPlayerFunc{|inchans, pitchEnv=true|
		var bufplayerfunc = if(pitchEnv, 
			{
				{|env, buffer, rate=1, trigger=1, start=0, loop=1, pitchenv=0.5|
					// Lag added to pitch envelope to seperate it from amplitude envelope
					var pitchenv = env.lag.range(1-pitchenv*rate, rate);

					PlayBuf.ar(
						inchans, 
						buffer, 
						pitchenv * BufRateScale.kr(buffer),  
						trigger,  
						start * BufFrames.kr(buffer),  
						loop
					)
				}
			},
			{
				{|env, buffer, rate=1, trigger=1, start=0, loop=1|
					PlayBuf.ar(
						inchans, 
						buffer, 
						rate * BufRateScale.kr(buffer),  
						trigger,  
						start * BufFrames.kr(buffer),  
						loop
					)
				}
			}
		);

		^bufplayerfunc
	}
}
