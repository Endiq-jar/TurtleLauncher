//
// TurtleLauncher: the awt_headless module previously had no LOCAL_SRC_FILES at
// all, which some ndk-build versions refuse ("no source files"); give it a real
// (dummy) translation unit so the library is always produced. Nothing links
// against it here - awt_xawt.so just needs libawt_headless.so to exist.
//
void awt_headless_stub(void) {
}
