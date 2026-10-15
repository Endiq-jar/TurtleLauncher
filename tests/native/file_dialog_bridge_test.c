#include <assert.h>
#include <stdio.h>
#include "../../TurtleLauncher/src/main/native/file_dialog_bridge.c"

static PathSet *fixture(void) {
    PathSet *set = calloc(1, sizeof(*set));
    set->count = 2;
    set->paths = calloc(2, sizeof(char *));
    set->paths[0] = strdup("/files/a space.txt");
    set->paths[1] = strdup("/files/हिन्दी.zip");
    return set;
}
int main(void) {
    Filter filters[] = {{"Video", "mp4,mkv"}, {"Archives", "zip"}};
    char *spec = filter_string(filters, 2);
    assert(strcmp(spec, "mp4,mkv;zip;") == 0); free(spec);
    assert(filter_string(NULL, 2) == NULL);
    assert(filter_string(filters, 257) == NULL);
    char *out = (char *)1;
    assert(single(0, &out, NULL, NULL) == 2 && out == NULL); // no Android host
    assert(single(0, NULL, NULL, NULL) == 0);
    assert(nfd_open_with(NULL, NULL, LONG(&out), 0) == 2 && !out);
    assert(nfd_multi_with(NULL, NULL, LONG(&out), 0) == 2 && !out);
    assert(nfd_save_with(NULL, NULL, LONG(&out), 0) == 2 && !out);
    assert(nfd_folder_with(NULL, NULL, LONG(&out), 0) == 2 && !out);
    PathSet *set = fixture();
    unsigned count = 0;
    assert(set_count(NULL, NULL, LONG(set), LONG(&count)) == 1 && count == 2);
    assert(set_count(NULL, NULL, 0, LONG(&count)) == 0);
    assert(set_path(NULL, NULL, LONG(set), 1, LONG(&out)) == 1);
    assert(strcmp(out, "/files/हिन्दी.zip") == 0); free(out);
    assert(set_path(NULL, NULL, LONG(set), -1, LONG(&out)) == 0 && !out);
    assert(set_path(NULL, NULL, LONG(set), 2, LONG(&out)) == 0 && !out);
    PathEnum *it = NULL;
    assert(set_enum(NULL, NULL, LONG(set), LONG(&it)) == 1);
    for (unsigned i = 0; i < 2; ++i) {
        assert(enum_next(NULL, NULL, LONG(&it), LONG(&out)) == 1);
        assert(strcmp(out, set->paths[i]) == 0); free(out);
    }
    assert(enum_next(NULL, NULL, LONG(&it), LONG(&out)) == 1 && !out);
    enum_free(NULL, NULL, LONG(&it)); assert(!it);
    set_free(NULL, NULL, LONG(set));
    set = fixture();
    LegacySet legacy = {0};
    assert(legacy_pack(set, &legacy) == 1);
    free_set(set);
    assert(legacy.count == 2 && legacy.indices[0] == 0);
    assert(legacy_count(NULL, NULL, LONG(&legacy)) == 2);
    assert(strcmp(PTR(legacy_path(NULL, NULL, LONG(&legacy), 0)), "/files/a space.txt") == 0);
    assert(!legacy_path(NULL, NULL, LONG(&legacy), 2));
    legacy_free_set(NULL, NULL, LONG(&legacy));
    assert(!legacy.buffer && !legacy.indices && !legacy.count);
    free_set(NULL);
    puts("Native file dialog ABI/memory tests passed");
}
