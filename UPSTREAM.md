# Upstream attribution

The provider and extractor files marked with SPDX headers are adapted from
[neoser1984/neowatch](https://github.com/neoser1984/neowatch/tree/13b96229ed698251827568ff914ba3b6830e16c9)
at commit `13b96229ed698251827568ff914ba3b6830e16c9`, licensed under GPL-3.0. Copyright remains with the
original contributors. The complete license is included in LICENSE.

Imported modules: DiziBox, DiziMom, DiziPal, Dizilla, FilmMakinesi, FilmModu,
FullHDFilmizlesene, HDFilmCehennemi, SelcukFlix, SezonlukDizi, WebteIzle.
DiziYou, SetFilmIzle and the common domain registry remain maintained here.

Local adaptations: package namespace, dynamic domain resolution at API entry
points, bookmark URL rewriting, shared document request retries, and reporting
success only when a playback link is actually emitted. Source is published in
this repository alongside distributed .cs3 files. Upstream extractor notices
and contributor credits are retained.

The DiziPal and Dizilla main provider classes were subsequently reimplemented against the currently observed public XHR/RSC formats; their upstream-derived extractor helpers retain the original GPL attribution. Rhino-only fallback execution was replaced by the host WebView path for compatibility with NuvioTV.
