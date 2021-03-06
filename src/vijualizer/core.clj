(ns vijualizer.core
  (:require [clojure.core.async :as a]
            [quil.core :as q]
            [quil.middleware :as qm])
  (:import [ddf.minim Minim]
           [ddf.minim.analysis BeatDetect FFT]))


;;; Minim

;; Emulation of Processing-Environment functions

;; Defining an interface so it is possible to add types,
;; which is necessary for the class
(definterface MinimInput
  (^String sketchPath [^String filename])
  (^java.io.InputStream createInput [^String filename]))


(def MinimDummyInput
  (proxy [MinimInput] []
    (sketchPath [filename] "")
    (createInput [filename] nil)))


(defn init-line []
  "Initializes Minim and an InputLine"
  (let [frame-size 1024
        sample-rate 44100
        minim (Minim. MinimDummyInput)
        input (.getLineIn minim Minim/MONO frame-size sample-rate)]
    {:sample-rate sample-rate
     :frame-size frame-size
     :minim minim
     :input input}))


(defn stop-line [line]
  (.close (:input line))
  (.stop (:minim line)))


;;; FFT stuff

(defn make-init-state []
  (atom {:stop false
         :xs [] :ys []}))


(defn update-state-once [state fft]
  (-> state
      (assoc :xs (->> (range (.avgSize fft))
                      (map #(.getAverageCenterFrequency fft %))))
      (assoc :ys (->> (range (.avgSize fft))
                      (map #(.getAvg fft %))))))


(defn stop-update [state]
  (swap! state assoc :stop true))


(defn update-state [state args]
  (let [fft (FFT. (:frame-size args) (:sample-rate args))]
    (.logAverages fft 45 15)
    (a/go (while (not (:stop @state))
            (.forward fft (.-mix (:input args)))
            (swap! state update-state-once fft)
            (Thread/sleep 10)))))


(defn restart-update
  "for interactive use"
  [state args]
  (stop-update state)
  (Thread/sleep 400)
  (swap! state assoc :stop false)
  (update-state state args))


;;; quil frequency spectrum

(def CONF
  {:w 600
   :h 400})


(defn quil-setup []
  (q/frame-rate 50)
  (q/color-mode :rgb 1.0))


(defn draw-freq-spectrum [state]
  "state is an atom"
  (q/background 0.2)
  (let [s @state]
    (doseq [i (range (count (:xs s)))
            :let [x (* i 4)
                  h (+ 2 (nth (:ys s) i))]]
      #_(q/rect x 0 4 (* 10 (/ (Math/log (* 1 h)) ;; linearly scaled scaling
                             (Math/log (+ 1.1 (- 1 (/ i 135)))))))
      #_(q/rect x 0 4 h) ;; linear scaling
      (q/rect x 0 4 (* (+ 1 (* 2 (/ (+ 1 i) 136))) h)))))


(defn quil-freq-spectrum [state]
  (q/sketch
   :title "plot"
   :size [(:w CONF) (:h CONF)]
   :setup quil-setup
   :update identity
   :draw (fn [_] (draw-freq-spectrum state))
   :middleware [qm/fun-mode]))


;;; waveform

#_(defn cache-ys [a]
  (defn get-y [i] (-> (:input a) .-mix (.get i)))
  (doall (map get-y (range (:frame-size a)))))


(defn draw-waveform [a]
  (q/background 0.5)
  (q/text (str [(q/width) (q/height)]) 0 0 500 500)
  (let [get-y (fn [i] (-> (:input a) .-mix (.get i)))
        hh (/ (q/height) 2)
        scale-x (fn [x] (-> x (/ (:frame-size a))
                            (* (q/width))))
        scale-y (fn [y] (-> y (* hh) (+ hh)))]
    (doseq [i (-> (:frame-size a) dec range)
            :let [j (inc i)]]
      (q/line (scale-x i) (scale-y (get-y i))
              (scale-x j) (scale-y (get-y j))))))


(defn plot-waveform [a]
  (q/sketch
   :size [500 500]
;   :size :fullscreen
   :setup quil-setup
   :update identity
   :draw (fn [_] (draw-waveform a))
   :middleware [qm/fun-mode]))


;;; docs

;; in repl:
;; (def a (init-line)) ;; a dict
;; (def state (make-init-state)) ;; an atom
;; (update-state state a)
;; ... (quil-plot state)
;; (stop-update state)
;; (stop-line a)
