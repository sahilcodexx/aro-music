# InnerTubeX source integration for Aro

These sources come from the locally available InnerTubeX v0.7.4 sources archive (`com.github.MetrolistGroup:innertubex:v0.7.4`), with Android/JVM adaptations: common and JVM sources are combined, Kotlin expect/actual declarations are replaced with ordinary JVM functions, and the solver scripts from Aro's working Android fork are retained as resources. The module uses Aro's Kotlin serialization and Ktor versions.

Upstream: https://github.com/MetrolistGroup/innertubex/tree/v0.7.4

The source is built as an Android library instead of requiring an unpublished Maven-local AAR. Preserve the included GPL v3 LICENSE and upstream attribution. Playback regression tests must run whenever this module or its dependencies change.
