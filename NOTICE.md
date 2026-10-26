# Attribution and licensing

TACZ Client Renderer is an independent, unofficial client renderer inspired by
and containing code derived from [Timeless and Classics Guns Zero (TACZ)](https://github.com/MCModderAnchor/TACZ).
It is not an official TACZ release and is not endorsed by its maintainers.

Upstream reference: `b482eff8c94a733ac8d0910193fca3893954027c`.
The Bedrock coordinate conversions, face/UV conventions, view-locator inversion
and hand-locator conventions were adapted from TACZ's `BedrockModel`,
`BedrockCubePerFace`, `BedrockPolygon`, `FaceUVsItem`, `BedrockPart`,
`FirstPersonRenderGunEvent` and functional hand renderers. Credit belongs to
MCModderAnchor and the upstream TACZ contributors for their original work.
This project changes those implementations for Fabric 26.2, vanilla-item mappings,
local presentation timelines and a reduced rendering pipeline.

Code is distributed under GNU GPL version 3; see [LICENSE](LICENSE).
The upstream project licenses its assets separately under CC BY-NC-ND 4.0.
No TACZ gun pack, texture, sound, animation file or Lua script is distributed here
or inside the mod JAR. Obtain assets separately and follow their authors' terms.

The optional tool examples contain newly authored simple animation clips and
geometry generated from vanilla item sprite silhouettes. They reference the
installed game's `minecraft:` textures; no Minecraft texture images are included.
Minecraft and its original assets belong to Mojang/Microsoft. This project is not
affiliated with Mojang or Microsoft. The project license does not grant rights
to third-party assets.

The Gradle Wrapper is provided by Gradle under Apache License 2.0.
