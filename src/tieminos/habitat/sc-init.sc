//https://doc.sccode.org/Reference/EmacsEditor.html
(
Can.init;
// Server.default.options.inDevice_("Scarlett 18i20 USB");
// Server.default.options.outDevice_("Scarlett 18i20 USB");
o = Server.default.options;
s.options.maxLogins = 8;
o.numInputBusChannels = 30;
o.numOutputBusChannels = 46;
s.waitForBoot({
	().play;
	s.makeGui; // `l` to show meter https://doc.sccode.org/Classes/Server.html
    // TODO figure out a way to mix in all outs for the spectrogram... or something
    {In.ar(0)}.spectrogram;
});
)
