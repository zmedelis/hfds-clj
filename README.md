## 🪣 hfds-clj

[![Clojars Project](https://img.shields.io/clojars/v/io.github.zmedelis/hfds-clj.svg)](https://clojars.org/io.github.zmedelis/hfds-clj)

**hfds-clj** is a lib to help you get to the [HuggingFace datasets](https://huggingface.co/datasets) and [HuggingFace models](https://huggingface.co/models). 

The lib provides seamless access to datasets via this process:
* *downloading* HF dataset,
* *caching* downloaded set locally, and
* *serving* it from there for subsequent requests.

It does not aim to replicate the full range of functionality found in the [HuggingFace datasets library](https://huggingface.co/docs/datasets/v2.14.5/en/index). Though as an immediate extension, it would be great to support [Dataset Features](https://huggingface.co/docs/datasets/v2.14.5/en/about_dataset_features).

## Download datasets


### CLI

Data sets can be downloaded from the command line
```
clojure -X:download-dataset :dataset "allenai/prosocial-dialog"
```

See next section for parameter description.

### Code

```clojure
(require '[hfds-clj.datasets :refer [load-dataset]])
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
                :hfds/cache-dir     "/data"
                :hfds/limit         4000}))
```

## Downloads models

Models can be downloaded and stored on disk via this CLI call:
```
clojure -X:download-model :model '"nvidia/Gemma-2b-it-ONNX-INT4"' :hf-token "<huggingface API token>" :models-base-dir '"/tmp/models"'
```


# Usage as Clojure tool
'hfds-clj'  can be used as well as a Clojure tool.

Installation as tool (latest GIT version)

```bash
clojure -Ttools install io.github.zmedelis/hfds-clj '{:git/url "https://github.com/zmedelis/hfds-clj" :git/sha "4a84254030fceca8bf3f5e8dce4226b4b8cdf48a"}' :as hfds-clj
```
Example to to call it as tool, to download data/model:

```bash
clojure -Thfds-clj hfds-clj.datasets/download-cli :dataset "allenai/prosocial-dialog"
clojure -Thfds-clj hfds-clj.models/download-cli :model '"nvidia/Gemma-2b-it-ONNX-INT4"' :hf-token "<token>" :models-base-dir '"/tmp/models"'

```



## Notes

* This is extracted from [Bosquet](https://github.com/zmedelis/bosquet) where HuggingFace datasets are used for LLM related developments.
* Thanks to [TrueGrit](https://github.com/KingMob/TrueGrit) helping to rebustly fetch data from HF API
