     .global vsc_blob
     .global vsc_blob_size
     .section .rodata
 vsc_blob:
 #if defined __i686__
     .incbin "cs-x86.tgz"
 #elif defined __x86_64__
     .incbin "cs-x86_64.tgz"
 #elif defined __aarch64__
     .incbin "cs-aarch64.tgz"
 #elif defined __arm__
     .incbin "cs-arm.tgz"
 #else
 # error Unsupported arch
 #endif
 1:
 vsc_blob_size:
     .int 1b - vsc_blob
