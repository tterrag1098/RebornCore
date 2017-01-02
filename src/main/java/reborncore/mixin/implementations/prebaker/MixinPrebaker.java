/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reborncore.mixin.implementations.prebaker;

import com.google.common.io.ByteStreams;
import javassist.ClassPool;
import javassist.CtClass;
import reborncore.mixin.MixinManager;
import reborncore.mixin.api.Rewrite;
import reborncore.mixin.json.JsonUtil;
import reborncore.mixin.transformer.IMixinRemap;
import reborncore.mixin.transformer.MixinTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

/**
 * This is based of asie's work for fabric. Thanks <3
 */
public class MixinPrebaker {

	private static class DesprinklingFieldVisitor extends FieldVisitor {
		public DesprinklingFieldVisitor(int api, FieldVisitor fv) {
			super(api, fv);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
			if (isSprinkledAnnotation(desc)) {
				return null;
			}
			return super.visitAnnotation(desc, visible);
		}
	}

	private static class DesprinklingMethodVisitor extends MethodVisitor {
		public DesprinklingMethodVisitor(int api, MethodVisitor mv) {
			super(api, mv);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
			if (isSprinkledAnnotation(desc)) {
				return null;
			}
			return super.visitAnnotation(desc, visible);
		}
	}

	private static class DesprinklingClassVisitor extends ClassVisitor {
		public DesprinklingClassVisitor(int api, ClassVisitor cv) {
			super(api, cv);
		}

		@Override
		public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
			return new DesprinklingFieldVisitor(Opcodes.ASM5, super.visitField(access, name, desc, signature, value));
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
			return new DesprinklingMethodVisitor(Opcodes.ASM5, super.visitMethod(access, name, desc, signature, exceptions));
		}

		@Override
		public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
			if (isSprinkledAnnotation(desc)) {
				return null;
			}
			return super.visitAnnotation(desc, visible);
		}
	}

	private static boolean isSprinkledAnnotation(String desc) {
		//System.out.println(desc);
		return desc.startsWith("Lorg/spongepowered/asm/mixin/transformer/meta");
	}

	// Term proposed by Mumfrey, don't blame me
	public static byte[] desprinkle(byte[] cls) {
		ClassReader reader = new ClassReader(cls);
		ClassWriter writer = new ClassWriter(0);

		reader.accept(new DesprinklingClassVisitor(Opcodes.ASM5, writer), 0);
		return writer.toByteArray();
	}

	public static void main(String[] args) throws IOException {
		if (args.length < 3) {
			System.out.println("usage: MixinPrebaker <mixin-config> <input-jar> <output-jar> <lib-jars...>");
			return;
		}

		MixinPrebaker mixinPrebaker = new MixinPrebaker();
		for (int i = 3; i < args.length; i++) {
			mixinPrebaker.addFile(new File(args[i]));
		}

		MixinManager.registerMixinConfig(JsonUtil.mixinConfigurationFromFile(new File(args[0])));
		preBake(new File(args[1]), new File(args[2]));
	}

	public static void preBake(File inputJar, File outputJar) {
		MixinManager.mixinRemaper = new DummyRemapper();
		MixinManager.logger = LogManager.getLogger("MixinPrebaker");

		URLClassLoader ucl = (URLClassLoader) MixinPrebaker.class.getClassLoader();
		Launch.classLoader = new LaunchClassLoader(ucl.getURLs());
		Launch.blackboard = new HashMap<>();

		MixinTransformer mixinTransformer = new MixinTransformer();

		if(outputJar.exists()){
			outputJar.delete();
		}
		try {
			JarInputStream input = new JarInputStream(new FileInputStream(inputJar));
			JarOutputStream output = new JarOutputStream(new FileOutputStream(outputJar));
			JarEntry entry;
			while ((entry = input.getNextJarEntry()) != null) {
				if (entry.getName().endsWith(".class")) {
					byte[] classIn = ByteStreams.toByteArray(input);
					String className = entry.getName().substring(0, entry.getName().length() - 6).replace('/', '.');
					byte[] classOut = mixinTransformer.transform(className, className, classIn);
					if (classIn != classOut) {
						System.out.println("Transformed " + className);
						classOut = desprinkle(classOut);
					}
					JarEntry newEntry = new JarEntry(entry.getName());
					newEntry.setComment(entry.getComment());
					newEntry.setSize(classOut.length);
					newEntry.setLastModifiedTime(FileTime.from(Instant.now()));
					output.putNextEntry(newEntry);
					output.write(classOut);
				} else {
					output.putNextEntry(entry);
					ByteStreams.copy(input, output);
				}
			}

			//			output.putNextEntry(new JarEntry(FabricMixinBootstrap.APPLIED_MIXIN_CONFIGS_FILENAME));
			//			output.write(Strings.join(FabricMixinBootstrap.getAppliedMixinConfigs(), "\n").getBytes(Charsets.UTF_8));

			input.close();
			output.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static class DummyRemapper implements IMixinRemap {
		@Override
		public void remap(CtClass mixinClass, ClassPool classPool) {

		}

		@Override
		public Optional<Pair<String, String>> getFullTargetName(Rewrite rewriteAnn, String name) {
			return Optional.empty();
		}
	}


	public void addFile(File file) throws IOException {
		URLClassLoader sysloader = (URLClassLoader) this.getClass().getClassLoader();
		Class sysclass = URLClassLoader.class;
		try {
			Method method = sysclass.getDeclaredMethod("addURL", URL.class);
			method.setAccessible(true);
			method.invoke(sysloader, file.toURI().toURL());
		} catch (Throwable t) {
			if (t.getMessage() != null) {
				System.out.println(t.getMessage());
			}
			throw new IOException("Error, could not add File to system classloader");
		}
	}

}
