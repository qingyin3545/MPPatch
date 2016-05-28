/**
    Copyright (C) 2015-2016 Lymia Aluysia <lymiahugs@gmail.com>

    Permission is hereby granted, free of charge, to any person obtaining a copy of
    this software and associated documentation files (the "Software"), to deal in
    the Software without restriction, including without limitation the rights to
    use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
    of the Software, and to permit persons to whom the Software is furnished to do
    so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all
    copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
    SOFTWARE.
*/

#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>
#include <string.h>
#include <stdio.h>
#include <time.h>

#include "c_rt.h"
#include "platform.h"

// Debug logging
#ifdef DEBUG
    FILE* debug_log_file;
    __attribute__((constructor(150))) static void initDebugLogging() {
        debug_log_file = fopen("mvmm_debug.log", "w");
    }
#else
    #define debug_print(format, ...)
#endif

// String manipulation
bool endsWith(const char* str, const char* ending) {
    size_t str_len = strlen(str), ending_len = strlen(ending);
    return str_len >= ending_len && !strcmp(str + str_len - ending_len, ending);
}

// std::list implementation
CppListLink* CppListLink_alloc(int length) {
    CppListLink* link = (CppListLink*) malloc(sizeof(CppListLink) + length);
    link->next = link;
    link->prev = link;
    return link;
}
void* CppListLink_newLink(CppListLink* list, int length) {
    CppListLink* link = CppListLink_alloc(length);

    link->prev = list->prev;
    link->next = list;

    list->prev->next = link;
    list->prev       = link;

    return link->data;
}
void CppListLink_clear(CppListLink* list) {
    CppListLink* link = list->next;
    while(link != list) {
        CppListLink* nextLink = link->next;
        free(link);
        link = nextLink;
    }

    list->prev = list;
    list->next = list;
}
void CppListLink_free(CppListLink* list) {
    CppListLink_clear(list);
    free(list);
}

// Actual patch code!
static UnpatchData* writeRelativeJmp(void* targetAddress, void* hookAddress, bool isCall, const char* reason) {
    // Register the patch for unpatching
    UnpatchData* unpatch = malloc(sizeof(UnpatchData));
    unpatch->offset = targetAddress;
    memcpy(unpatch->oldData, targetAddress, 5);

    // Actually generate the patch opcode.
    int offsetDiff = (int) hookAddress - (int) targetAddress - 5;
    debug_print("Writing %s (%s) - %p => %p (diff: 0x%08x)",
        isCall ? "CALL" : "JMP", reason, targetAddress, hookAddress, offsetDiff);

    char opcode = isCall ? 0xe8 : 0xe9;
    *((char*)(targetAddress    )) = opcode;
    *((int *)(targetAddress + 1)) = offsetDiff;

    return unpatch;
}
static UnpatchData* patchAddress(void* targetAddress, void* hookAddress, bool isCall, const char* reason) {
    memory_oldProtect protectFlags;
    unprotectMemoryRegion(targetAddress, 5, &protectFlags);
    UnpatchData* unpatch = writeRelativeJmp(targetAddress, hookAddress, isCall, reason);
    protectMemoryRegion(targetAddress, 5, &protectFlags);
    return unpatch;
}
UnpatchData* doPatch(AddressDomain domain, int address, void* hookAddress, bool isCall, const char* reason) {
    void* targetAddress = resolveAddress(domain, address);
    char reason_buf[1024];
    snprintf(reason_buf, 1024, "patch: %s", reason);
    return patchAddress(targetAddress, hookAddress, isCall, reason_buf);
}
void unpatch(UnpatchData* data) {
    memory_oldProtect protectFlags;
    debug_print("Unpatching at %p", data->offset);
    unprotectMemoryRegion(data->offset, 5, &protectFlags);
    memcpy(data->offset, data->oldData, 5);
    protectMemoryRegion(data->offset, 5, &protectFlags);
    free(data);
}

// Jmplist code
typedef struct jmplist_type {
    int32_t exists;
    int32_t addr;
    int32_t isSymbol;
    int32_t target;
    const char* string;
} __attribute__((packed)) jmplist_type;
extern jmplist_type jmplist_CV_BINARY       [] __asm__("cif_jmplist_CV_BINARY"       );
extern jmplist_type jmplist_CV_GAME_DATABASE[] __asm__("cif_jmplist_CV_GAME_DATABASE");

static void doJmplist(AddressDomain domain, const char* domainName, jmplist_type* jmplist) {
    debug_print("Initializing jmplist for %s", domainName);
    for(jmplist_type* t = jmplist; t->exists; t++) {
        void* targetAddress = !t->isSymbol ? resolveAddress(domain, t->target              ) :
                                             resolveSymbol (domain, (const char*) t->target);
        char buffer[1024];
        snprintf(buffer, 1024, "jmplist initialization for %s (%s)", domainName, t->string);
        free(patchAddress((void*) t->addr, targetAddress, false, buffer));
    }
}
__attribute__((constructor(250))) static void initJmps() {
    doJmplist(CV_BINARY       , "CV_BINARY"       , jmplist_CV_BINARY       );
    doJmplist(CV_GAME_DATABASE, "CV_GAME_DATABASE", jmplist_CV_GAME_DATABASE);
}