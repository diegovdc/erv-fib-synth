(ns tieminos.compositions.7D-percusion-ensamble.exploration3
  (:require
   [clojure.data.generators :refer [weighted]]
   [erv.scale.core :as scale]
   [overtone.core :as o]
   [overtone.midi :as midi]
   [tieminos.midi.core :refer [all-notes-off get-iac2!]]
   [tieminos.midi.plain-algo-note :refer [malgo-note]]
   [tieminos.polydori.analysis.dorian-hexanies :refer [dorian-hexanies-in-polydori]]
   [tieminos.polydori.scale :refer [polydori-v2]]
   [tieminos.sc-utils.groups.v1 :as groups]
   [tieminos.utils :refer [map-subscale-degs rrange]]
   [time-time.dynacan.players.gen-poly :refer [on-event ref-rain]]
   [time-time.dynacan.players.gen-poly :as gp]
   [time-time.standard :refer [rrand]]))

(def sink (midi/midi-out "VirMIDI"))

(def iac2 (get-iac2!))

(defn bh "Blackhole outs (starts on chan 3)" [out] (+ 22 out))

(defn my-malgo
  [config]
  (malgo-note (merge {:sink sink
                      :scale-size 29
                      :base-midi-deg 60
                      :base-midi-chan 0}
                     config)))

(defn- deg->freq [& {:keys [base-freq scale degree]}]
  (scale/deg->freq (:scale polydori-v2)
                   base-freq
                   (map-subscale-degs (count (:scale polydori-v2))
                                      (:degrees
                                       (nth
                                         dorian-hexanies-in-polydori
                                         scale))
                                      degree)))


(defn diat->polydori-degree
  [scale degree]
  (map-subscale-degs (count (:scale polydori-v2))
                     (:degrees
                      (nth
                        dorian-hexanies-in-polydori
                        scale))
                     degree))

(o/defsynth low
  [freq 85
   amp 0.5
   mod-freq 8300
   pan 0
   atk 0.01
   dcy 1
   out 0]
  (o/out out (-> (o/range-lin (o/pulse mod-freq) (- freq 15) (+ freq 15))
                 o/sin-osc
                 (o/pan2 pan)
                 (* (o/env-gen (o/env-perc atk dcy) :action o/FREE))
                 (* amp (o/amp-comp-a freq)))))

(o/defsynth short-plate
  [freq 200
   amp 0.5
   mod-freq 1000
   pan 0
   atk 0.01
   dcy 1
   out 0]
  (o/out out (-> (o/range-lin (o/pulse mod-freq) (- freq 200) (+ freq 200))
                 o/sin-osc
                 (o/pan2 pan)
                 (* (o/env-gen (o/env-perc atk dcy) :action o/FREE))
                 (* amp (o/amp-comp-a freq)))))

(def synths (map #(partial % (groups/early)) [low short-plate]))

(defn init! []
  (groups/init-groups!))

(def mempan
  (memoize (fn [deg-mod]
             (rrand -1.0 1))))

(comment
  (init!)
  (gp/stop)
  (all-notes-off sink)
;;;;;;;;;;;;
;;; INTRO
;;;;;;;;;;;;;
  (init!)
  
  (ref-rain
    :id ::1 :durs [3 2 2] :ratio 1/9
    :on-event (on-event
                (let [synth (rand-nth synths)
                      deg (at-i [0])]
                  (synth
                    :freq (deg->freq :base-freq 200 :scale 0 :degree deg)
                    :mod-freq (rrand 6000 10000)
                    :pan (mempan (mod deg 2))
                    :amp (rrange 0.45 0.51)
                    :out (bh 0))
                  #_(my-malgo {:deg (diat->polydori-degree 0 deg) :dur 0.1 :vel 100}))))

  (ref-rain
    :id ::1 :durs [3 2 2] :ratio 1/9
    :on-event (on-event
                (let [synth (rand-nth synths)
                      deg (at-i [0 2])]
                  (synth
                    :freq (deg->freq :base-freq 200 :scale 0 :degree deg)
                    :mod-freq (rrand 6000 10000)
                    :pan (mempan (mod deg 2))
                    :amp (rrange 0.45 0.51)
                    :out (bh 0))
                  #_(when (> (rand ) 0.6)
                      (my-malgo {:deg (diat->polydori-degree 0 (+ (at-i [4 -4 3 -4 -3]) deg)) :dur (weighted {1 9 1.5 8}) :vel 100})))))

  (ref-rain
    :id ::1 :durs [3 2 2] :ratio 1/9
    :on-event (on-event
                (let [synth (rand-nth synths)
                      deg (at-i [0 2 1 (at-i [2 3]) 4])]
                  (synth
                    :freq (deg->freq :base-freq 200 :scale 0 :degree deg)
                    :mod-freq (rrand 6000 10000)
                    :pan (mempan (mod deg 2))
                    :amp (rrange 0.45 0.51)
                    :out (bh 0))
                  ;; TODO agregar silencions con when-not
                  #_(when (> (rand ) 0.6)
                      (my-malgo {:deg (diat->polydori-degree 0 (+ (at-i [0 -4 0 -4 -3]) deg)) :dur (weighted {1 9 1.5 8}) :vel 100}))
                  #_(if (> (rand) 0.5)
                      (my-malgo {:base-midi-chan 0 :deg (diat->polydori-degree 0 (+ (at-i [-4  0 -4 -4 0 -4 0]) deg)) :dur (weighted {1 9 1.5 8}) :vel 100}))
                  #_(when (> (rand) 0.4))
                  #_(my-malgo {:deg (diat->polydori-degree 0 (+ -1 (at-i [2 2 3 2 3]) (weighted {0 5 4 4 }) deg)) :dur (weighted {0.1 9 1 5}) :vel 100})
                  #_(my-malgo {:base-midi-chan 0 :deg deg :dur 0.1 :vel 100}))))

  (ref-rain
    :id ::1 :durs [3 2 2] :ratio 1/9
    :on-event (on-event
                (let [synth (rand-nth synths)
                      deg (at-i [0 2 1 (at-i [2 3])])]
                  (synth
                    :freq (deg->freq :base-freq 200 :scale 0 :degree deg)
                    :mod-freq (rrand 6000 10000)
                    :amp (rrange 0.45 0.51)
                    :out (bh 0))
                  #_(my-malgo {:base-midi-chan 0 :deg (+ (at-i [2 2 3 2 3]) (weighted {0 5 4 4}) deg) :dur (weighted {0.1 9 1 5}) :vel 100})
                  #_(my-malgo {:base-midi-chan 0 :deg (+ 6 deg) :dur 0.1 :vel 100}))))



  (ref-rain
    :id ::1 :durs [3 2 2] :ratio 1/9
    :on-event (on-event
                (let [synth (rand-nth synths)
                      deg (at-i [0 2 1 (at-i [2 3]) 4])]
                  (synth
                    :freq (deg->freq :base-freq 200 :scale 0 :degree deg)
                    :mod-freq (rrand 6000 10000)
                    :amp (rrange 0.45 0.51)
                    :out (bh 0))
                  #_(my-malgo {:base-midi-chan 0 :deg (+ (at-i [2 2 3 2 3]) (weighted {0 5 4 4}) deg) :dur (weighted {0.1 9 1 5}) :vel 100})
                  #_(my-malgo {:base-midi-chan 0 :deg (+ 6 deg) :dur 0.1 :vel 100})
                  #_(my-malgo {:base-midi-chan 0 :deg deg :dur 0.1 :vel 100}))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Comineza transición 1
;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  (ref-rain
    :id ::1 :durs [3 2 2] :ratio 1/9
    :on-event (on-event
                (let [synth (rand-nth synths)
                      deg (at-i [0 2 1 (at-i [2 3]) (at-i [7 4]) (at-i [4 5 ]) 3])]
                  (synth
                    :freq (deg->freq :base-freq 200 :scale 0 :degree deg)
                    :mod-freq (rrand 6000 10000)
                    :out (bh 0))
                  (when (or true ( #{1 3 5} (mod (inc index) 7)))
                    (synth
                      :freq (deg->freq :base-freq 200 :scale 2 :degree deg)
                      :mod-freq (rrand 6000 10000)
                      :out (bh (case deg -7 2 0))))
                  (when (or #_true (#{5 10 13} (mod (inc index) 14)))
                    (my-malgo {:deg (diat->polydori-degree 2 (+  (weighted {-6 5 }) deg)) :dur (weighted {0.1 9 1 5}) :vel 100}))
                  #_(my-malgo {:base-midi-chan 0 :deg deg :dur 0.1 :vel 100}))))

  (ref-rain ;; NOTE evol previous
    :id ::1 :durs [3 2 2] :ratio 1/9
    :on-event (on-event
                (let [synth (rand-nth synths)
                                        ; [0 5 5] 2 [1 -7] 8 [4 7 3] [6 5] [3 3 8 9]
                      deg (at-i [(at-i [0 #_ #_5 5])
                                 2
                                 (at-i [1 -7])
                                 (at-i [8  #_ #_2 8])
                                 (at-i [4 7 #_3])
                                 (at-i [6 5])
                                 (at-i [3  #_ #_ #_3 8 9])])]
                  (synth
                    :freq (deg->freq :base-freq 200 :scale 0 :degree deg)
                    :mod-freq (rrand 6000 10000)
                    :out (bh (case deg -7 2 0)))
                  (when (or true (#{3} (mod index 5)))
                    (synth
                      :freq (deg->freq :base-freq (at-i [200 #_100]) :scale 2 :degree deg)
                      :mod-freq (rrand 6000 10000)
                      :out (bh (case deg -7 2 0))))
                  (my-malgo {:deg (diat->polydori-degree 0 (+ #_6 (at-i [2 2 3 2 3]) (weighted {0 5 4 4 -4 4}) deg)) :dur (weighted {0.1 9 1 5}) :vel 10})
                  (if (> (rand) 0.3)
                    ;; NOTE keep vel 10 at first

                    (my-malgo {:deg (diat->polydori-degree 2 (+ #_6 (at-i [2 2 3 2 3]) (weighted {0 5 4 4 -4 4}) deg)) :dur (weighted {0.1 9 1 5}) :vel (at-i [10 30 60])})
                    ;; FIXME has a -2 midi note
                    #_(my-malgo {:deg (diat->polydori-degree 1 (+ 7
                                                                (at-i [-6 -12 6 0 0 0 ]) ;; NOTE add later
                                                                (weighted {0 5 4 4 -4 4}) deg)) :dur (weighted {0.1 9 1 5 2 5}) :vel (at-i [10 30])}))
                  #_(my-malgo {:base-midi-chan (case deg -7 1 0) :deg deg :dur 0.1 :vel 100}))))


  (ref-rain
    :id ::1 :durs [3 2 2] :ratio 1/9
    :on-event (on-event
                (let [synth (rand-nth synths)
                      deg (at-i [(at-i [0 5 5])
                                 2
                                 (at-i [1 -7])
                                 (at-i [8 2 8])
                                 #_(at-i [4 7 3])
                                 #_(at-i [6 15 10 6])
                                 (at-i [3 3 8 9])])
                      scale-1 (weighted {0 0 9 1})]
                  (synth
                    :freq (deg->freq :base-freq 200 :scale scale-1 :degree deg)
                    :mod-freq (rrand 6000 10000)
                    :amp 0.3
                    :out (bh (case deg -7 2 0)))
                  (when (or #_true (#{3 6} (mod index 7)))
                    (synth
                      :freq (deg->freq :base-freq (at-i [200 #_100]) :scale 2 :degree deg)
                      :mod-freq (rrand 6000 10000)
                      :out (bh (case deg -7 2 0))))
                  #_(my-malgo {:deg (diat->polydori-degree scale-1 (+ 6 (at-i [2 2 3 2 3]) (weighted {0 5 4 4 -4 4}) deg)) :dur (weighted {0.1 9 1 5}) :vel 10})
                  #_(if (> (rand) 0.7)
                    ;; NOTE keep vel 10 at first

                    (my-malgo {:deg (diat->polydori-degree 2 (+ 6 (at-i [2 2 3 2 3]) (weighted {0 5 4 4 -4 4}) deg)) :dur (weighted {0.1 9 1 5}) :vel (at-i [10 30 60])})
                    ;; FIXME has a -2 midi note
                    #_ (my-malgo {:deg (diat->polydori-degree 1 (+ 7
                                                                   (at-i [-6 -12 6 0 0 0]) ;; NOTE add later
                                                                   (weighted {0 5 4 4 -4 4}) deg)) :dur (weighted {0.1 9 1 5 2 5}) :vel (at-i [10 30])}))
                  #_ (my-malgo {:base-midi-chan (case deg -7 1 0) :deg deg :dur 0.1 :vel 100}))))
  (ref-rain
    :id ::1 :durs [3 2 2 3 2 2 3 2 2 3] :ratio 1/9
    :on-event (on-event
                (let [synth (rand-nth synths)
                      deg (at-i [(at-i [0 5])
                                 (at-i [2 ])
                                 #_(at-i [1 -7])
                                 (at-i [8])
                                 #_(at-i [4 7 5])
                                 #_(at-i [6 15 10 6])
                                 #_(at-i [3 3 8 9])])
                      scale-1 (weighted {0 0 6 0 9 7})
                      scale-2 (weighted {2 0 6 4 9 0})]
                  (when (> (rand) 0.3 )
                    (synth
                      :freq (deg->freq :base-freq 200 :scale scale-1 :degree deg)
                      :mod-freq (rrand 6000 10000)
                      :amp 0.1
                      :dcy 3
                      :out (bh (case deg -7 2 0))))
                  (when (or (#{3 6} (mod index 5)))
                    (synth
                      :freq (deg->freq :base-freq (at-i [100]) :scale scale-2 :degree deg)
                      :mod-freq (rrand 6000 10000)
                      :amp 0.1
                      :dcy 3
                      :out (bh (case deg -7 2 0))))
                  (when (> (rand) 0.4 )
                    (my-malgo {:deg (diat->polydori-degree scale-1 (+ 0 (at-i [2 3 3 2 3]) (weighted {0 5 4 4 -4 4}) deg)) :dur (weighted {0.1 9 2 5 3 1}) :vel 100}))
                  (when (> (rand) 0.4 )
                    (my-malgo {:deg (diat->polydori-degree scale-2 (+ -2 (at-i [2 2 3 2 3]) (weighted {0 5 4 4 -4 4}) deg)) :dur (weighted {0.1 5 1 5 4 2}) :vel 60}))
                  #_(if (> (rand) 0.7)
                      ;; NOTE keep vel 10 at first

                      (my-malgo {:deg (diat->polydori-degree scale-2 (+ 6 (at-i [2 2 3 2 3]) (weighted {0 5 4 4 -4 4}) deg)) :dur (weighted {0.1 9 1 5}) :vel (at-i [10 30 60])})
                      ;; FIXME has a -2 midi note
                      #_ (my-malgo {:deg (diat->polydori-degree 1 (+ 7
                                                                     (at-i [-6 -12 6 0 0 0]) ;; NOTE add later
                                                                     (weighted {0 5 4 4 -4 4}) deg)) :dur (weighted {0.1 9 1 5 2 5}) :vel (at-i [10 30])}))
                  #_ (my-malgo {:base-midi-chan (case deg -7 1 0) :deg deg :dur 0.1 :vel 100}))))

  (ref-rain
    :id ::2 :durs [2 1 2 ] :ratio 1/9
    :ref ::1
    :on-event (on-event
                (let [synth (rand-nth synths)
                      deg (+ 12  (at-i [(at-i [0 5 5])
                                        2
                                        #_(at-i [1 -7])
                                        #_(at-i [8 2 8])
                                        (at-i [4])
                                        (at-i [6 15 10 6])
                                        #_(at-i [3 3 8 9])]))
                      scale-1 (weighted {0 0 9 1})]
                  (synth
                    :freq (deg->freq :base-freq 200 :scale scale-1 :degree deg)
                    :mod-freq (rrand 6000 10000)
                    :amp 0.3
                    :out (bh (case deg -7 2 0))))))


  (gp/stop ::2)

  :7d-perc)
