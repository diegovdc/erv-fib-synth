(ns tieminos.habitat.extended-sections.polinizadores-nocturnos.main
  (:require
   [clojure.data.generators :refer [weighted]]
   [erv.cps.core :as cps]
   [erv.scale.core :as scale]
   [overtone.core :as o]
   [taoensso.timbre :as timbre]
   [tieminos.habitat.extended-sections.polinizadores-nocturnos.emisiones-refrain
    :refer [polinizadores-nocturnos]]
   [tieminos.habitat.extended-sections.ui.v1 :refer [add-rec-bar]]
   [tieminos.habitat.init :refer [habitat-initialized? init!]]
   [tieminos.habitat.main :as main]
   [tieminos.habitat.main-sequencer :as hseq :refer [subsequencer]]
   [tieminos.habitat.osc :refer [args->map init responder]]
   [tieminos.habitat.parts.noche :as noche]
   [tieminos.habitat.recording :as rec :refer [norm-amp]]
   [tieminos.habitat.routing
    :refer [guitar-processes-main-out
            inputs
            percussion-processes-main-out
            preouts]]
   [tieminos.habitat.scratch.sample-rec2
    :refer [rising-upwards start-rec-loop!]]
   [tieminos.habitat.utils :refer [open-inputs-with-rand-pan]]
   [tieminos.overtone-extensions :as oe]
   [tieminos.sc-utils.synths.v1 :refer [lfo]]
   [tieminos.utils :refer [rrange]]
   [time-time.dynacan.players.gen-poly :as gp :refer [on-event ref-rain]]
   [time-time.standard :refer [rrand]]))

(oe/defsynth sample-player*
  [buf 0
   out 0
   dur 1
   rate 1
   ps-rate 1
   a 0.1
   r 0.1
   amp 1]
  (let [s (max 0 (- dur a r))
        ps-mix (lfo (o/rand 0 1) 0 1)
        sig (o/play-buf :num-channels 1 :bufnum buf :rate rate)]
    (o/out out (-> (+ (* (- 1 ps-mix) sig)
                      (* ps-mix (o/pitch-shift sig 0.2 ps-rate)))
                   (o/pan4 (lfo (o/rand 0 1) -1 1)
                           (lfo (o/rand 0 1) -1 1))
                   (o/free-verb (lfo (o/rand 0 0.4) 0.2 1)
                                (lfo (o/rand 0 2) 0 2))
                   (* amp (o/env-gen (o/envelope [0 1 1 0] [a s r])
                                     :action o/FREE))))))
(defn start-layer-recording!
  [input-k]
  (if-let [bus (:bus (input-k @inputs))]
    (rec/rec-input {:section "polinizadores-nocturnos"
                    :subsection "layer"
                    :input-name (:name bus)
                    :input-bus bus
                    :dur-s 10
                    :countdown 5
                    :on-end (fn [_] (println (format "========== %s Recording done ============"
                                                     (name input-k))))
                    :on-rec-start add-rec-bar})
    (timbre/error "Input not found")))

(defn play-layer!
  [buf]
  (let [out (if (:input-name (:rec/meta buf))
              guitar-processes-main-out
              percussion-processes-main-out)
        rate (weighted {1 20
                        1/2 1
                        2 3
                        2/3 3
                        3/2 3
                        5/4 2
                        11/8 1
                        9/8 1
                        8/9 1
                        7/8 1
                        7/4 1
                        (rrand 0.5 1) 1})
        ps-rate (weighted {1 10
                           3/2 3
                           5/4 2
                           7/4 1
                           7/8 2
                           11/4 2})
        dur (/ (:duration buf) rate)
        amp   (* (rrand 0.01 0.25) (let [amp-norm (:amp-norm-mult buf)]
                                   ;; TODO check and improve
                                   (cond (> amp-norm 5) (/ amp-norm 2)
                                         :else amp-norm)))]
    (-> (sample-player* {:buf buf
                         :dur dur
                         :rate rate
                         :ps-rate ps-rate
                         :a (* 0.2 dur)
                         :r (* 0.4 dur)
                         :amp amp
                         :out out}))))

(do
  ;; FIXME refactor
  (defn start-layers-refrain!
    [bus-name-str]
    (ref-rain
      :id (keyword "layers" bus-name-str)
      :durs (map (fn [_] (rrand 3.0 8))
                 (range 100))
      :on-event (on-event
                  (when-let [bufs (seq (->> @rec/bufs
                                           vals
                                           (filter #(and (-> % :rec/meta
                                                             ((juxt :input-name :section :subsection))
                                                             (= [bus-name-str "polinizadores-nocturnos" "layer"]))))))]
                    (play-layer! (rand-nth bufs)))))))

(->> @rec/bufs vals (map :rec/meta))

(defn polinizadores-nocturnos*
  [context]
  (polinizadores-nocturnos context)

  (subsequencer
    :sequencer/polinizadores-nocturnos
    context
    (let [scale-1 (->> (cps/make 3 [9 13 15 21 27 31]) :scale)
          make-sample-player-config (fn [scale]
                                      {:buf-fn (fn [_] (-> @rec/bufs vals rand-nth))
                                       :period-dur 20
                                       :total-durs 20
                                       :loop? true
                                       :refrain-id :rising-upwards-loop
                                       :synth-params (fn [{:keys [buf i]}]
                                                       {:amp (* (rrange 0.2 1) (norm-amp buf))
                                                        :rate (scale/deg->freq scale 1 (+ (mod i 43)))})})]
      [[[52 22] (fn [_]
                  (start-rec-loop!)
                  (start-layers-refrain! "guitar-bus")
                  (start-layers-refrain! "mic-1-bus")
                  (start-layers-refrain! "mic-2-bus")
                  (start-layers-refrain! "mic-3-bus"))]
       #_[[53 0] (fn [_] (rising-upwards (make-sample-player-config scale-1)))]
       [[55 0] (fn [_] #_(rising-upwards (-> (make-sample-player-config scale-1)
                                           (assoc :period-dur 4))))]
       [[55 10] (fn [_]
                  #_(gp/stop :rising-upwards-loop)
                  #_(reset! rec/bufs {}))]
       [[57 0] (fn [_] #_(rising-upwards (make-sample-player-config scale-1)))]
       [[59 0] (fn [_] #_(reset! rec/bufs {}))]
       [[60 0] (fn [_] #_(rising-upwards (-> (make-sample-player-config scale-1)
                                           (assoc :period-dur 4))))]
       [[60 10] (fn [_] #_(rising-upwards (make-sample-player-config scale-1)))]
       [[61 0] (fn [_]
                 #_(reset! rec/bufs {})
                 #_(rising-upwards (-> (make-sample-player-config scale-1)
                                     (assoc :period-dur 4))))]
       [[62 10] (fn [_]
                  (gp/stop :rec-loop)
                  (gp/stop ::layers)
                 ( gp/stop :rising-upwards-loop))]])))

(def polinizadores-nocturnos-main
  ;; TODO revisar refrains de emision hay cosas raras (aumentos de volumen y saturación del servidor)
  {:context (merge main/context {})
   :sections [[[52 22] #'polinizadores-nocturnos*]
              [[62 10] (fn [_] (println "end"))]]
   :initial-sections #'polinizadores-nocturnos*
   ;; :rec? true
   })

(comment
  (reset! rec/recording? {})
  (reset! rec/bufs {})
  (main/start-sequencer! polinizadores-nocturnos-main)
  (-> @hseq/context)

  (timbre/set-level! :info)
  (do (when @habitat-initialized?
        (reset! rec/recording? {})
        (reset! rec/bufs {})
        (main/stop-sequencer! hseq/context))
      (init!))

  (start-layer-recording! :guitar)
  (gp/stop)
  ;; only for testing
  (open-inputs-with-rand-pan
    {:inputs inputs
     :preouts preouts})
  (do
    (start-layers-refrain! "guitar-bus")
    (start-layers-refrain! "mic-1-bus")
    (start-layers-refrain! "mic-2-bus")
    (start-layers-refrain! "mic-3-bus"))

  (:name (:bus (:guitar @inputs)))
  (-> @rec/bufs)
  ;; TouchOSC
  (init :port 16180)
  (responder
    (fn [{:keys [path args] :as msg}]
      (let [args-map (args->map args)]
        (case path
          "/rec" (start-layer-recording! (:input args-map))
          (println "Unknown path for message: " msg))))))
