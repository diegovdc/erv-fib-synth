(ns tieminos.midi.core
  (:require
   [overtone.midi :as midi]
   [overtone.sc.node :refer [ctl node?]]
   [taoensso.timbre :as timbre]))

;; If getting the error Device is Busy:
;; https://linuxmusicians.com/viewtopic.php?p=133696
;; http://www.tedfelix.com/linux/linux-midi.html
;; HOWEVER is MUCH BETTER to use `sudo modprobe snd-virmidi` to
;; enable virmidi

;; NOTE on virmidi, if overtone starts to crash on load, maybe virmidi was corrupted (has already happend to me once)

(defonce ks (try (midi/midi-in "VirMIDI")
                 (catch Exception e
                   (timbre/warn (str "Could not connect to VirMIDI: " (.getMessage e))))))

(defonce oxygen (try (midi/midi-in "USB MIDI")
                     (catch Exception e
                       (timbre/warn (str "Could not connect to USB MIDI: " (.getMessage e))))))

(defonce oxygen* (atom nil))
(defn get-oxygen!
  []
  (if @oxygen*
    @oxygen*
    (try (reset! oxygen* (midi/midi-in "USB MIDI"))
         (catch Exception e
           (timbre/warn (str "Could not connect to USB MIDI: " (.getMessage e)))))))

(defonce lumatone* (atom nil))

(defn get-lumatone!
  []
  (if @lumatone*
    @lumatone*
    (try (reset! lumatone* (midi/midi-in "Lumatone"))
         (catch Exception e
           (timbre/warn (str "Could not connect to Lumatone: " (.getMessage e)))))))

(comment
  ;; basic USAGE
  (midi-in-event
    :midi-input oxygen
    :note-on (fn [_] (println "pepe"))))

(comment
  (midi/midi-out "VirMIDI")
  (midi/midi-in)
  (midi/midi-devices)
  (midi/midi-sources)
  (midi/midi-sinks))

(defn note-on
  "`f` receives a map with the following keys
   `'(:data2 :command :channel :msg :note :status :data1 :device :timestamp :velocity)`"
  ([f] (note-on ks f))
  ([midi-input f]
   (println midi-input f)
   (midi/midi-handle-events
    midi-input
    (fn [ev]
      (try
        (cond
          (-> ev :command (= :note-on)) (f ev)
          :else nil)
        (catch Exception e (timbre/error "MIDIError" e)))))))

(defonce synths (atom {}))

(defn add-synth [ev synth]
  (let [midi-note (:note ev)]
    (when (and (not (get @synths midi-note))
               (or (node? synth)
                   (and (seq synth) (every? node? synth))))
      (swap! synths assoc midi-note synth))))

(defn remove-synth [ctl ev]
  (let [synth (@synths (:note ev))]
    (cond
      (node? synth) (do (ctl synth :gate 0)
                        (swap! synths dissoc (:note ev)))
      (and (seq synth) (every? node? synth)) (do (doseq [s synth] (ctl s :gate 0))
                                                 (swap! synths dissoc (:note ev)))
      :else nil)))

(defn get-auto-gate-ctl [auto-ctl]
  (if auto-ctl
    {:add add-synth :remove (partial remove-synth ctl)}
    {:add (fn [_ _] nil) :remove (fn [_] nil)}))

(defn- handle-midi-event
  [ev {:keys [note-on note-off auto-ctl]}]
  (try
    (let [cmd (:command ev)
          gate-ctl (get-auto-gate-ctl auto-ctl)]
      (cond
        (and (= cmd :note-on) note-on) ((gate-ctl :add) ev (note-on ev))
        (and (= cmd :note-off) note-off) (do (note-off ev)
                                             ((gate-ctl :remove) ev))
        :else nil))
    (catch Exception e (timbre/error "MIDIError" e))))

(defn midi-in-event
  "`note` events receive a map with the following keys
   `'(:data2 :command :channel :msg :note :status :data1 :device :timestamp :velocity)`"
  [& {:keys [midi-input note-on note-off auto-ctl]
      :or {midi-input oxygen
           auto-ctl true
           note-off (fn [_] nil)}}]
  (midi/midi-handle-events
   midi-input
   (fn [ev] (handle-midi-event ev
                               {:note-on note-on
                                :note-off note-off
                                :auto-ctl auto-ctl}))))

#_(defn all-notes-off [sink] (midi/midi-control sink 123 0))
(defn all-notes-off [sink]
  (doseq [n (range 128) chan (range 16)]
    (midi/midi-note-off sink n chan)))
(comment
  (note-on (fn [_] (println (:note _))))
  (all-notes-off oxygen)
  (midi-in-event :note-on (fn [_] (println "on" ((juxt :channel :note) _)))
                 :note-off (fn [_] (println "off" ((juxt :channel :note) _)))))
