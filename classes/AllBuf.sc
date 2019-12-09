AllBuf {
	*addSynth{
		arg inchans=2,
			outchans=2,
			pitchEnv = true,
			lpf=true,
			filterEnv=true,
			verbose=false;

		var name = "buf%i%".format(inchans, outchans);
		var panmsg, filtmsg; // For verbosity
		var panfunc, bufplayerfunc, envfunc, synthfunc, filterfunc;


		// The pan function
		// The precise function used is dependent on amount of input and output channels
		// For audio with more than 2 in and 2 out at the same time, Splay functions are used
		panfunc = case
		// Mono in
		{(inchans == 1).and(outchans == 1)} { 
			panmsg = "Setting pan function to mono (no panning)";
			{|sig| sig }
		}
		{(inchans == 1).and( outchans == 2 )} { 
			panmsg = "Setting pan function to mono to stereo ";
			{|sig, pan| 
				Pan2.ar(sig, pan) 
			}
		}
		{(inchans == 1).and(outchans > 2)} {
			panmsg = "Setting pan function to multichan (% channels)".format(outchans);
			{|sig, pan=0.0, width=2.0, orientation=0.5| 
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
			panmsg = "Summing signal (no panning)";
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
			panmsg = "Setting pan function to stereo";
			{|sig, pan=0.5| 
				Balance2.ar(sig[0], sig[1], pos: pan) 
			}
		}
		{(inchans > 2).and(outchans == 2)} {
			panmsg = "Setting pan function to splay";
			{|sig, pan=0.5, spread=1, width=2, orientation=0.5| 
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
			panmsg = "Setting pan function to splay";
			{|sig, pan=0.5, spread=1, width=2, orientation=0.5| 
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
		}
		;

		envfunc = {|dur=1, attack=0.1, sustain=1, release=0.9, curve=4|
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

		// The core buffer player function
		// Optionally with a pitch envelope

		bufplayerfunc = if(pitchEnv, 
			{
				name = name ++ "pe";

				{|env, buffer, rate=1, trigger=1, start=0, loop=1, penv=0.5|
					// Lag added to pitch envelope to seperate it from amplitude envelope
					var pitchenv = env.lag.range(1-penv*rate, rate);

					PlayBuf.ar(
						inchans, 
						buffer, 
						pitchenv * BufRateScale.kr(buffer),  
						trigger,  
						start * BufDur.kr(buffer),  
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
						start * BufDur.kr(buffer),  
						loop
					)
				}
			}
		);

		// Add low pass filter
		// Optionally with a filter envelope

		filterfunc = if(lpf, 
			{
				if(filterEnv,
					{
						filtmsg = "Adding Low pass filter with envelope";
						name = name ++ "lpfe";

						{|in, env, cutoff=20000, resonance=0.5, fenv=0.5|
							// Lag added to filter envelope to seperate it from amplitude envelope
							var filterenv = env.lag2.range(1-fenv*cutoff, cutoff).clip(20.0,20000.0);

							DFM1.ar(in, filterenv,  resonance,  noiselevel: 0.0)
						}

					},
					{
						filtmsg = "Adding Low pass filter";
						name = name ++ "lpf";

						{|in, env, cutoff=20000, resonance=0.5|
							DFM1.ar(in, cutoff.clip(20.0,20000.0),  resonance,  noiselevel: 0.0)
						}
					}
				);

			}, {
				filtmsg = "Not adding Low pass filter";
				{|in| in }
			}
		);

		// The final patch
		synthfunc = {|dur=1, amp=1, out=0|

			var env = SynthDef.wrap(envfunc, prependArgs: [dur]); 
			var sig = SynthDef.wrap(bufplayerfunc, prependArgs: [env]);

			sig = SynthDef.wrap(panfunc, prependArgs:[sig]);
			sig = SynthDef.wrap(filterfunc,  prependArgs: [sig, env]);

			Out.ar(out, env * sig * amp);
		};

		verbose.if({
			"Making SynthDef '%'".format(name).postln;
			"inchans: %, outchans: %".format(inchans,outchans).postln;
			panmsg.postln;
			filtmsg.postln;
			"----------".postln;
		});


		// Make and add the SynthDef
		SynthDef.new(name.asSymbol, synthfunc).add;
	}

	*addSynths{
		arg outchans=2,
		maxchansIn=32,
		verbose=true;

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

	}
}
