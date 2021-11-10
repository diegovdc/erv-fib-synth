(ns erv-fib-synth.songs.happy-song
  (:require [erv.cps.core :as cps]
            [clojure.set :as set]
            [erv.utils.core :as utils]
            [erv.utils.core :as utils]
            [erv.utils.conversions :as conv]
            [overtone.core :as o]
            [erv-fib-synth.midi.mpe :as midi-mpe]
            [erv-fib-synth.synths :as synths]
            [erv.scale.core :as scale]
            [erv.neji.core :as neji]
            [erv.edo.core :as edo]))




(comment
  (o/connect-external-server)

  (o/recording-start "/home/diego/Desktop/happy-song-a-la-chiapaneca")
  (o/recording-stop)


  (add-watch osc/synths nil
             (fn [_ _ _ state]
               (->> (keys state)
                    (map #(-> (utils/wrap-at (-  % 60)  scale)
                              :set))
                    (#(if (seq %)
                        (let [diff (apply set/intersection %)]
                          [diff
                           (set/difference (apply set/union %) diff)
                           (set %)]) %))
                    println)))

  (def scale (:scale (cps/make 2 [1 3 5 7])))  ; happy song
  (def scale (:scale (cps/make 2 [1 3 5 11]))) ; a la chiapaneca
  (defn get-note [midi-note]
    (scale/deg->freq scale 200 (- midi-note 60)))
  (osc/midi-event
   :note-on #(do
               #_(sini (scale/deg->freq scale 200 (- (:note %) 60)
                                        :debug-fn (juxt :bounded-ratio :diff))
                       (/ (:vel %) 150)
                       :sust 0.5
                       :rel 0.1)

               (synths/low2
                (get-note (:note %))
                (+ 0.2 (/ (:vel %) 100))
                :mod-amp (+ 0.1 (/ (:vel %) 140))
                :mod-freq 4000))
   #_(mpe/mpe-note-on
      :scale scale
      :base-freq 200
      :deg-offset -60
      :get-pitch-class mpe/get-cps-pitch-class
      :midi-note (:note %)
      :vel (:vel %)))
  )
