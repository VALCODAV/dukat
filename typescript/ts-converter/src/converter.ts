import {DukatLanguageServiceHost} from "./DukatLanguageServiceHost";
import {AstConverter} from "./AstConverter";
import * as ts from "typescript";
import {FileResolver} from "./FileResolver";
import {AstFactory} from "./ast/AstFactory";
import {SourceFileDeclaration} from "./ast/ast";
import * as declarations from "declarations";
import {DeclarationResolver} from "./DeclarationResolver";
import {ExportContext} from "./ExportContext";
import {DocumentCache} from "./DocumentCache";
import {DependencyBuilder} from "./DependencyBuilder";

function createFileResolver(): FileResolver {
  return new FileResolver();
}

let cache = new DocumentCache();

function getLibPaths(program: ts.Program, libPath: ts.SourceFile | undefined, libs: Set<string> = new Set()): Set<string> {
  if (libPath === undefined) {
    return libs;
  }

  let value = ts.normalizePath(libPath.fileName);
  if (libs.has(value)) {
    return libs;
  }

  libs.add(value);
  libPath.libReferenceDirectives.forEach(libReference => {
    getLibPaths(program, program.getLibFileFromReference(libReference), libs);
  });

  return libs;
}

class SourceBundleBuilder {
  private astFactory = new AstFactory();
  private program: ts.Program;
  private libsSet: Set<string>;

  private isLibSource(node: ts.Node | string): boolean {
    let src = (typeof node == "string") ? node : (node.getSourceFile().fileName);
    return this.libsSet.has(ts.normalizePath(src));
  }

  private astConverter: AstConverter;
  private dependencyBuilder: DependencyBuilder;

  constructor(
    private stdLib: string,
    originalFiles: Array<string>
  ) {
    this.program = this.createProgram(originalFiles);
    this.libsSet = getLibPaths(this.program, this.program.getSourceFile(this.stdLib));

    let dependencyBuilder = new DependencyBuilder(this.program, new ExportContext((node: ts.Node) => this.isLibSource(node)));
    originalFiles.forEach(file => {
      let sourceFile = this.program.getSourceFile(file);
      if (sourceFile) {
        dependencyBuilder.buildDependencies(sourceFile);
      }
    });

    this.dependencyBuilder = dependencyBuilder;


    this.astConverter = new AstConverter(
      new ExportContext((node: ts.Node) => this.isLibSource(node)),
      this.program.getTypeChecker(),
      new DeclarationResolver(this.program),
      this.astFactory
    );
  }


  private createProgram(files: Array<string>): ts.Program {
    let host = new DukatLanguageServiceHost(createFileResolver(), this.stdLib);
    files.forEach(fileName => host.register(fileName));
    let languageService = ts.createLanguageService(host, (ts as any).createDocumentRegistryInternal(void 0, void 0, cache || void 0));
    const program = languageService.getProgram();

    if (program == null) {
      throw new Error("failed to create languageService");
    }

    return program;
  }

  createBundle(): declarations.SourceBundleDeclarationProto {

    let sourceSetBundle = new declarations.SourceBundleDeclarationProto();

    let sourceSet = new declarations.SourceSetDeclarationProto();

    let sourceFiles = new Array<SourceFileDeclaration>();
    this.dependencyBuilder.forEachDependency(dep => {
      let packageName = this.astFactory.createIdentifierDeclarationAsNameEntity(this.isLibSource(dep.fileName) ? "<LIBROOT>" : "<ROOT>");
      sourceFiles.push(this.astConverter.createSourceFileDeclaration(this.program.getSourceFile(dep.fileName), packageName, node => dep.accept(node)));
    });

    sourceSet.setSourcesList(sourceFiles);
    sourceSetBundle.setSourcesList([sourceSet]);
    return sourceSetBundle;
  }
}

export function createBundle(stdlib: string, files: Array<string>) {
  return new SourceBundleBuilder(stdlib, files).createBundle();
}