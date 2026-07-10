# Third-party components & licenses

esochan is licensed under the **GNU General Public License v3.0** (see
[`LICENSE.txt`](LICENSE.txt)). esochan is a fork of
[Overchan Android](https://github.com/miku-nyan/Overchan-Android) by miku-nyan,
also GPLv3; miku-nyan's copyright is retained in the source file headers.

The application bundles, or is derived from, the third-party components listed
below. Their respective copyright notices and licenses are reproduced here as
required by those licenses. Full texts of the licenses referenced in the table
follow in the [License texts](#license-texts) section.

## Bundled source & native code

| Component | Files | Origin | Copyright | License |
|-----------|-------|--------|-----------|---------|
| **GIFLIB** | `jni/giflib/*` (`dgif_lib.c`, `gifalloc.c`, `gif_lib.h`, `gif_lib_private.h`) | giflib | © 1997 Eric S. Raymond | GIFLIB (MIT/X11-style) — see also [`jni/giflib/COPYING`](jni/giflib/COPYING) |
| **android-gif-drawable** | `jni/gif.c`, `jni/gif.h`, `src/dev/esoc/esochan/lib/gifdrawable/*` | [koral--/android-gif-drawable](https://github.com/koral--/android-gif-drawable) | © 2013–present Karol Wrótniak "koral--" | MIT |
| **DragSortListView** | `src/dev/esoc/esochan/lib/dslv/*` | [bauerca/drag-sort-listview](https://github.com/bauerca/drag-sort-listview) | © Carl Bauer and contributors | Apache-2.0 |
| **subsampling-scale-image-view** (fixed 2014 snapshot) | `src/dev/esoc/esochan/lib/gallery/FixedSubsamplingScaleImageView.java` | [davemorrissey/subsampling-scale-image-view](https://github.com/davemorrissey/subsampling-scale-image-view) | © 2014 David Morrissey | Apache-2.0 |
| **VerticalViewPager** | `src/dev/esoc/esochan/lib/gallery/verticalviewpager/*` | AOSP `ViewPager`, vertical port by Martin Sonc (@LambergaR) | © 2011 The Android Open Source Project | Apache-2.0 |
| **DragGripView** | `src/dev/esoc/esochan/lib/DragGripView.java` | DashClock, via [Clover](https://github.com/Floens/Clover) | © 2013 Google Inc. | Apache-2.0 |
| **SwipeDismissListViewTouchListener** | `src/dev/esoc/esochan/lib/SwipeDismissListViewTouchListener.java` | [romannurik/Android-SwipeToDismiss](https://github.com/romannurik/Android-SwipeToDismiss) | © 2013 Google Inc. | Apache-2.0 |
| **ViewPager touch-fix / WebView helpers** | `src/dev/esoc/esochan/lib/gallery/ViewPagerFixed.java`, `WebViewFixed.java`, `JSWebView.java` | Chris Banes / androidquery `WebImage` / Stack Overflow | see per-file headers | Apache-2.0 (androidquery) / CC BY-SA (Stack Overflow snippets) |
| **Jpeg / TouchGifView helpers** | `src/dev/esoc/esochan/lib/gallery/Jpeg.java`, `TouchGifView.java` | [vortexwolf/2ch-Browser](https://github.com/vortexwolf/2ch-Browser) | 2ch-Browser authors | GPL (see 2ch-Browser) |
| **ClickableToast / WebViewProxy** | `src/dev/esoc/esochan/lib/ClickableToast.java`, `WebViewProxy.java` | Stack Overflow / public snippets (URLs in file headers) | see per-file headers | CC BY-SA / snippet |
| **MailDateFormat** | `src/dev/esoc/esochan/lib/MailDateFormat.java` | JavaMail (`javax.mail.internet.MailDateFormat`) | © 1997–2007 Sun Microsystems, Inc. | CDDL-1.0 or GPL-2.0 (dual) — see file header |

## Bundled assets

| Asset | Files | Origin | License |
|-------|-------|--------|---------|
| **Wakaba script** | `assets/wakaba3.js` | Wakaba imageboard software (Dag Ågren) | GPL |
| **Dollchan Extension Tools** | `assets/dollscript.js` | [Dollchan Extension Tools](https://github.com/SthephanShinkufag/Dollchan-Extension-Tools) | GPLv3 |
| **Imageboard stylesheets** | `assets/burichan.css`, `assets/futaba.css`, `assets/gurochan.css`, `assets/photon.css` | Classic Futaba/Burichan/Photon/Gurochan imageboard themes | Public imageboard styles |

## Runtime dependencies (distributed in the APK)

Resolved via Gradle (see `gradle/libs.versions.toml`):

| Dependency | Copyright | License |
|------------|-----------|---------|
| AndroidX (`core`, `fragment`, `lifecycle`, `viewpager`, `swiperefreshlayout`, `drawerlayout`, `annotation`, `cursoradapter`, `security-crypto`, `preference`) | © The Android Open Source Project | Apache-2.0 |
| Google Material Components (`com.google.android.material`) | © Google LLC | Apache-2.0 |
| OkHttp (`com.squareup.okhttp3`) | © Square, Inc. | Apache-2.0 |
| AndroidX Media3 / ExoPlayer (`androidx.media3`) | © The Android Open Source Project | Apache-2.0 |
| kotlinx.coroutines / Kotlin stdlib | © JetBrains s.r.o. | Apache-2.0 |
| Apache Commons Text (`org.apache.commons`) | © The Apache Software Foundation | Apache-2.0 |
| jsoup (`org.jsoup`) | © Jonathan Hedley | MIT |
| Kryo (`com.esotericsoftware:kryo5`) | © Esoteric Software LLC | BSD-3-Clause |
| JUnit (`junit`) — test only, not shipped | © the JUnit authors | EPL-1.0 |

---

# License texts

## GIFLIB license

```
The GIFLIB distribution is Copyright (c) 1997  Eric S. Raymond

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
```

## MIT License

Applies to android-gif-drawable (© 2013–present Karol Wrótniak "koral--") and
jsoup (© Jonathan Hedley).

```
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## BSD 3-Clause License

Applies to Kryo (© Esoteric Software LLC).

```
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.
3. Neither the name of the copyright holder nor the names of its contributors
   may be used to endorse or promote products derived from this software
   without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

## Apache License 2.0

Applies to the Apache-2.0 components listed above. The canonical text is
available at <https://www.apache.org/licenses/LICENSE-2.0>.

```
                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

   1. Definitions.

      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.

      "Licensor" shall mean the copyright owner or entity authorized by
      the copyright owner that is granting the License.

      "Legal Entity" shall mean the union of the acting entity and all
      other entities that control, are controlled by, or are under common
      control with that entity. For the purposes of this definition,
      "control" means (i) the power, direct or indirect, to cause the
      direction or management of such entity, whether by contract or
      otherwise, or (ii) ownership of fifty percent (50%) or more of the
      outstanding shares, or (iii) beneficial ownership of such entity.

      "You" (or "Your") shall mean an individual or Legal Entity
      exercising permissions granted by this License.

      "Source" form shall mean the preferred form for making modifications,
      including but not limited to software source code, documentation
      source, and configuration files.

      "Object" form shall mean any form resulting from mechanical
      transformation or translation of a Source form, including but
      not limited to compiled object code, generated documentation,
      and conversions to other media types.

      "Work" shall mean the work of authorship, whether in Source or
      Object form, made available under the License, as indicated by a
      copyright notice that is included in or attached to the work
      (an example is provided in the Appendix below).

      "Derivative Works" shall mean any work, whether in Source or Object
      form, that is based on (or derived from) the Work and for which the
      editorial revisions, annotations, elaborations, or other modifications
      represent, as a whole, an original work of authorship. For the purposes
      of this License, Derivative Works shall not include works that remain
      separable from, or merely link (or bind by name) to the interfaces of,
      the Work and Derivative Works thereof.

      "Contribution" shall mean any work of authorship, including
      the original version of the Work and any modifications or additions
      to that Work or Derivative Works thereof, that is intentionally
      submitted to Licensor for inclusion in the Work by the copyright owner
      or by an individual or Legal Entity authorized to submit on behalf of
      the copyright owner. For the purposes of this definition, "submitted"
      means any form of electronic, verbal, or written communication sent
      to the Licensor or its representatives, including but not limited to
      communication on electronic mailing lists, source code control systems,
      and issue tracking systems that are managed by, or on behalf of, the
      Licensor for the purpose of discussing and improving the Work, but
      excluding communication that is conspicuously marked or otherwise
      designated in writing by the copyright owner as "Not a Contribution."

      "Contributor" shall mean Licensor and any individual or Legal Entity
      on behalf of whom a Contribution has been received by Licensor and
      subsequently incorporated within the Work.

   2. Grant of Copyright License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      copyright license to reproduce, prepare Derivative Works of,
      publicly display, publicly perform, sublicense, and distribute the
      Work and such Derivative Works in Source or Object form.

   3. Grant of Patent License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      (except as stated in this section) patent license to make, have made,
      use, offer to sell, sell, import, and otherwise transfer the Work,
      where such license applies only to those patent claims licensable
      by such Contributor that are necessarily infringed by their
      Contribution(s) alone or by combination of their Contribution(s)
      with the Work to which such Contribution(s) was submitted. If You
      institute patent litigation against any entity (including a
      cross-claim or counterclaim in a lawsuit) alleging that the Work
      or a Contribution incorporated within the Work constitutes direct
      or contributory patent infringement, then any patent licenses
      granted to You under this License for that Work shall terminate
      as of the date such litigation is filed.

   4. Redistribution. You may reproduce and distribute copies of the
      Work or Derivative Works thereof in any medium, with or without
      modifications, and in Source or Object form, provided that You
      meet the following conditions:

      (a) You must give any other recipients of the Work or
          Derivative Works a copy of this License; and

      (b) You must cause any modified files to carry prominent notices
          stating that You changed the files; and

      (c) You must retain, in the Source form of any Derivative Works
          that You distribute, all copyright, patent, trademark, and
          attribution notices from the Source form of the Work,
          excluding those notices that do not pertain to any part of
          the Derivative Works; and

      (d) If the Work includes a "NOTICE" text file as part of its
          distribution, then any Derivative Works that You distribute must
          include a readable copy of the attribution notices contained
          within such NOTICE file, excluding those notices that do not
          pertain to any part of the Derivative Works, in at least one
          of the following places: within a NOTICE text file distributed
          as part of the Derivative Works; within the Source form or
          documentation, if provided along with the Derivative Works; or,
          within a display generated by the Derivative Works, if and
          wherever such third-party notices normally appear. The contents
          of the NOTICE file are for informational purposes only and
          do not modify the License. You may add Your own attribution
          notices within Derivative Works that You distribute, alongside
          or as an addendum to the NOTICE text from the Work, provided
          that such additional attribution notices cannot be construed
          as modifying the License.

      You may add Your own copyright statement to Your modifications and
      may provide additional or different license terms and conditions
      for use, reproduction, or distribution of Your modifications, or
      for any such Derivative Works as a whole, provided Your use,
      reproduction, and distribution of the Work otherwise complies with
      the conditions stated in this License.

   5. Submission of Contributions. Unless You explicitly state otherwise,
      any Contribution intentionally submitted for inclusion in the Work
      by You to the Licensor shall be under the terms and conditions of
      this License, without any additional terms or conditions.
      Notwithstanding the above, nothing herein shall supersede or modify
      the terms of any separate license agreement you may have executed
      with Licensor regarding such Contributions.

   6. Trademarks. This License does not grant permission to use the trade
      names, trademarks, service marks, or product names of the Licensor,
      except as required for reasonable and customary use in describing the
      origin of the Work and reproducing the content of the NOTICE file.

   7. Disclaimer of Warranty. Unless required by applicable law or
      agreed to in writing, Licensor provides the Work (and each
      Contributor provides its Contributions) on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
      implied, including, without limitation, any warranties or conditions
      of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
      PARTICULAR PURPOSE. You are solely responsible for determining the
      appropriateness of using or redistributing the Work and assume any
      risks associated with Your exercise of permissions under this License.

   8. Limitation of Liability. In no event and under no legal theory,
      whether in tort (including negligence), contract, or otherwise,
      unless required by applicable law (such as deliberate and grossly
      negligent acts) or agreed to in writing, shall any Contributor be
      liable to You for damages, including any direct, indirect, special,
      incidental, or consequential damages of any character arising as a
      result of this License or out of the use or inability to use the
      Work (including but not limited to damages for loss of goodwill,
      work stoppage, computer failure or malfunction, or any and all
      other commercial damages or losses), even if such Contributor
      has been advised of the possibility of such damages.

   9. Accepting Warranty or Additional Liability. While redistributing
      the Work or Derivative Works thereof, You may choose to offer,
      and charge a fee for, acceptance of support, warranty, indemnity,
      or other liability obligations and/or rights consistent with this
      License. However, in accepting such obligations, You may act only
      on Your own behalf and on Your sole responsibility, not on behalf
      of any other Contributor, and only if You agree to indemnify,
      defend, and hold each Contributor harmless for any liability
      incurred by, or claims asserted against, such Contributor by reason
      of your accepting any such warranty or additional liability.

   END OF TERMS AND CONDITIONS
```

## Other licenses referenced

- **GPLv3** (esochan itself, Overchan/miku-nyan, Wakaba, Dollchan Extension
  Tools, 2ch-Browser-derived helpers): see [`LICENSE.txt`](LICENSE.txt) or
  <https://www.gnu.org/licenses/gpl-3.0.html>.
- **CDDL-1.0 / GPL-2.0** (MailDateFormat, from JavaMail): the file retains its
  original Sun Microsystems dual-license header; see the header in
  `src/dev/esoc/esochan/lib/MailDateFormat.java` and
  <https://javaee.github.io/javamail/LICENSE>.
- **CC BY-SA** (Stack Overflow snippets, e.g. `WebViewProxy.java`,
  `ClickableToast.java`): origin URLs are recorded in each file's header.
