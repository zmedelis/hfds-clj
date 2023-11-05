## hfds-clj/hfds-clj

[![Clojars Project](https://img.shields.io/clojars/v/io.github.zmedelis/hfds-clj.svg)](https://clojars.org/io.github.zmedelis/hfds-clj)

**hfds-clj** is a lib to help you get to the [HuggingFace datasets](https://huggingface.co/datasets). The lib provides a single feature of seamlessly downloading HF datasets, saving downloaded sets in the cache, and serving it from there for subsequent requests.

It does not aim to replicate the full range of functionality found in the [HuggingFace datasets library](https://huggingface.co/docs/datasets/v2.14.5/en/index). Though as an immediate extension, it would be great to support [Dataset Features](https://huggingface.co/docs/datasets/v2.14.5/en/about_dataset_features).

## Usage

```clojure
(require '[hfds-clj.core :refer [load-dataset]])
```

Download HF datasets with this oneliner, where a single parameter is the dataset name as provided on the HF dataset page.

```clojure
(load-dataset "Anthropic/hh-rlhf")
```
The second call with `Anthropic/hh-rlhf` parameter will load it from the cache and return a lazy sequence of all the dataset records.

A more fine-grained data set request is supported via a parameterized call:

```clojure
(load-dataset  {:dataset "allenai/prosocial-dialog"
                          :split   "train"
                          :config  "default"
                          :offset  0
                          :length  100}
                         {:hfds/download-mode :reuse-dataset-if-exists
                          :hfds/limit         4000}))

```

## Notes

This is extracted from [Bosquet](https://github.com/zmedelis/bosquet) where HuggingFace datasets are used for LLM related developments.
