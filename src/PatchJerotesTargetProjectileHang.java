import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class PatchJerotesTargetProjectileHang {
    private static final String EXPECTED_SHA256 = "6ac96aaf9c1a895b2047a72bdbad0d13195de9a71ccadd5a755c02ef86af395c";
    private static final String OUTPUT_NAME = "jerotes-1.20.1-1.3.3-target-hang-fix.jar";
    private static final String CLASS_NAME = "com/jerotes/jerotes/entity/Shoot/Magic/MagicAbstractHurtingProjectile";
    private static final String CLASS_ENTRY = CLASS_NAME + ".class";
    private static final String TARGET_CLASS = "com/jerotes/jerotes/entity/Shoot/Magic/Target/BaseTargetEntity";
    private static final String ENTITY = "net/minecraft/world/entity/Entity";
    private static final String HELPER_NAME = "jerotesFix$targetHitResult";
    private static final String HIT_DESCRIPTOR = "(L" + ENTITY + ";Ljava/util/function/Predicate;)Lnet/minecraft/world/phys/HitResult;";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java -jar JerotesTargetProjectileHangFixer.jar <official-jerotes-1.3.3.jar>");
            System.exit(2);
        }
        Path input = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(input)) {
            throw new IOException("Input JAR not found: " + input);
        }
        String actualHash = sha256(Files.readAllBytes(input));
        if (!EXPECTED_SHA256.equals(actualHash)) {
            throw new IOException("Unsupported or modified input JAR.\nExpected SHA-256: " + EXPECTED_SHA256 + "\nActual SHA-256:   " + actualHash);
        }

        Path output = input.resolveSibling(OUTPUT_NAME);
        Path backup = input.resolveSibling(input.getFileName() + ".original-backup");
        if (Files.exists(output)) {
            throw new IOException("Output already exists; refusing to overwrite: " + output);
        }
        if (Files.exists(backup)) {
            if (!EXPECTED_SHA256.equals(sha256(Files.readAllBytes(backup)))) {
                throw new IOException("Backup exists but is not the expected original; refusing to overwrite: " + backup);
            }
        } else {
            Files.copy(input, backup);
            System.out.println("Original backup: " + backup);
        }

        Path temp = Files.createTempFile(output.getParent(), OUTPUT_NAME, ".tmp");
        try {
            writePatchedJar(input, temp);
            verifyJar(temp);
            Files.move(temp, output);
        } finally {
            Files.deleteIfExists(temp);
        }
        System.out.println("Fixed JAR: " + output);
        System.out.println("Success. Replace the original mod with the fixed JAR; do not install both.");
    }

    private static void writePatchedJar(Path source, Path target) throws IOException {
        try (
            ZipFile input = new ZipFile(source.toFile());
            ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(target))
        ) {
            Enumeration<? extends ZipEntry> entries = input.entries();
            while (entries.hasMoreElements()) {
                ZipEntry oldEntry = entries.nextElement();
                byte[] data;
                try (InputStream stream = input.getInputStream(oldEntry)) {
                    data = stream.readAllBytes();
                }
                switch (oldEntry.getName()) {
                    case CLASS_ENTRY -> data = patchClass(data);
                    case "META-INF/mods.toml" -> data = patchModsToml(data);
                    case "META-INF/MANIFEST.MF" -> data = patchManifest(data);
                    default -> { }
                }
                ZipEntry newEntry = new ZipEntry(oldEntry);
                newEntry.setSize(data.length);
                newEntry.setCompressedSize(-1);
                if (newEntry.getMethod() == ZipEntry.STORED) {
                    CRC32 crc = new CRC32();
                    crc.update(data);
                    newEntry.setCompressedSize(data.length);
                    newEntry.setCrc(crc.getValue());
                }
                output.putNextEntry(newEntry);
                output.write(data);
                output.closeEntry();
            }
        }
    }

    private static byte[] patchClass(byte[] originalClass) {
        ClassReader reader = new ClassReader(originalClass);
        ClassWriter writer = new ClassWriter(reader, 0);
        int[] patchedCalls = {0};
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals("m_8119_") || !descriptor.equals("()V")) {
                    return method;
                }
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC
                                && owner.equals("net/minecraft/world/entity/projectile/ProjectileUtil")
                                && name.equals("m_278158_")
                                && descriptor.equals(HIT_DESCRIPTOR)) {
                            patchedCalls[0]++;
                            super.visitMethodInsn(opcode, CLASS_NAME, HELPER_NAME, descriptor, false);
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                };
            }

            @Override
            public void visitEnd() {
                emitHelper(writer);
                super.visitEnd();
            }
        };
        reader.accept(visitor, 0);
        if (patchedCalls[0] != 1) {
            throw new IllegalStateException("Expected exactly one projectile raycast call, found " + patchedCalls[0]);
        }
        return writer.toByteArray();
    }

    private static void emitHelper(ClassWriter writer) {
        MethodVisitor method = writer.visitMethod(
            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
            HELPER_NAME,
            HIT_DESCRIPTOR,
            null,
            null
        );
        Label originalRaycast = new Label();
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitTypeInsn(Opcodes.INSTANCEOF, TARGET_CLASS);
        method.visitJumpInsn(Opcodes.IFEQ, originalRaycast);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ENTITY, "m_20182_", "()Lnet/minecraft/world/phys/Vec3;", false);
        method.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/core/Direction", "UP", "Lnet/minecraft/core/Direction;");
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ENTITY, "m_20183_", "()Lnet/minecraft/core/BlockPos;", false);
        method.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "net/minecraft/world/phys/BlockHitResult",
            "m_82426_",
            "(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/BlockHitResult;",
            false
        );
        method.visitInsn(Opcodes.ARETURN);
        method.visitLabel(originalRaycast);
        method.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "net/minecraft/world/entity/projectile/ProjectileUtil",
            "m_278158_",
            HIT_DESCRIPTOR,
            false
        );
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(4, 2);
        method.visitEnd();
    }

    private static byte[] patchModsToml(byte[] data) {
        String text = new String(data, java.nio.charset.StandardCharsets.UTF_8);
        text = replaceRequired(text, "version=\"1.3.3\"", "version=\"1.3.3-target-hang-fix-1\"");
        text = replaceRequired(text, "displayName=\"JerotesWarehouse\"", "displayName=\"JerotesWarehouse (Target Projectile Hang Fix)\"");
        return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] patchManifest(byte[] data) {
        String text = new String(data, java.nio.charset.StandardCharsets.UTF_8);
        return replaceRequired(text, "Implementation-Version: 1.3.3", "Implementation-Version: 1.3.3-target-hang-fix-1")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String replaceRequired(String text, String oldValue, String newValue) {
        int first = text.indexOf(oldValue);
        if (first < 0 || text.indexOf(oldValue, first + oldValue.length()) >= 0) {
            throw new IllegalStateException("Expected exactly one metadata value: " + oldValue);
        }
        return text.replace(oldValue, newValue);
    }

    private static void verifyJar(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry classEntry = zip.getEntry(CLASS_ENTRY);
            if (classEntry == null || zip.getEntry("META-INF/mods.toml") == null) {
                throw new IOException("Patched JAR verification failed");
            }
            byte[] data;
            try (InputStream stream = zip.getInputStream(classEntry)) {
                data = stream.readAllBytes();
            }
            ClassReader reader = new ClassReader(data);
            boolean[] helperFound = {false};
            int[] helperCalls = {0};
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (name.equals(HELPER_NAME) && descriptor.equals(HIT_DESCRIPTOR)) {
                        helperFound[0] = true;
                    }
                    if (!name.equals("m_8119_") || !descriptor.equals("()V")) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                            if (owner.equals(CLASS_NAME) && name.equals(HELPER_NAME) && descriptor.equals(HIT_DESCRIPTOR)) {
                                helperCalls[0]++;
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            if (!helperFound[0] || helperCalls[0] != 1) {
                throw new IOException("Patched bytecode verification failed");
            }
        }
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}
