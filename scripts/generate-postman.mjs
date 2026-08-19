import fs from "node:fs";
import path from "node:path";

const root = path.resolve(import.meta.dirname, "..");
const openApiPath = path.join(root, "postman", "canteiro-io.openapi.json");
const collectionPath = path.join(root, "postman", "canteiro-io.postman_collection.json");
const environmentPath = path.join(root, "postman", "canteiro-io.local.postman_environment.json");
const document = JSON.parse(fs.readFileSync(openApiPath, "utf8"));

function resolveSchema(schema) {
  if (!schema?.$ref) return schema ?? {};
  return schema.$ref.split("/").slice(1).reduce((value, segment) => value?.[segment], document) ?? {};
}

function exampleFor(schema, propertyName = "value", depth = 0) {
  schema = resolveSchema(schema);
  if (schema.example !== undefined) return schema.example;
  if (schema.default !== undefined) return schema.default;
  if (schema.enum?.length) return schema.enum[0];
  if (schema.oneOf?.length) return exampleFor(schema.oneOf[0], propertyName, depth + 1);
  if (schema.anyOf?.length) return exampleFor(schema.anyOf[0], propertyName, depth + 1);
  if (depth > 8) return null;
  if (schema.type === "array") return [exampleFor(schema.items, propertyName, depth + 1)];
  if (schema.type === "object" || schema.properties) {
    return Object.fromEntries(Object.entries(schema.properties ?? {})
      .filter(([, value]) => !resolveSchema(value).readOnly)
      .map(([name, value]) => [name, exampleFor(value, name, depth + 1)]));
  }
  if (schema.type === "boolean") return true;
  if (schema.type === "integer") return 1;
  if (schema.type === "number") return propertyName.toLowerCase().includes("price") ? 100 : 10;
  if (schema.format === "uuid" || propertyName.toLowerCase().endsWith("id")) return `{{${propertyName}}}`;
  if (schema.format === "date") return "2026-08-19";
  if (schema.format === "date-time") return "2026-08-19T12:00:00Z";
  if (schema.format === "email" || propertyName.toLowerCase().includes("email")) return "usuario@example.com";
  return propertyName;
}

function requestBody(operation) {
  const content = operation.requestBody?.content;
  if (!content) return {};
  const contentType = Object.keys(content)[0];
  const schema = resolveSchema(content[contentType]?.schema);
  if (contentType === "multipart/form-data") {
    return {
      header: [],
      body: {
        mode: "formdata",
        formdata: Object.entries(schema.properties ?? {}).map(([name, value]) => {
          const resolved = resolveSchema(value);
          if (resolved.format === "binary") return { key: name, type: "file", src: "" };
          return { key: name, type: "text", value: String(exampleFor(resolved, name)) };
        })
      }
    };
  }
  if (contentType === "application/json") {
    return {
      header: [{ key: "Content-Type", value: "application/json" }],
      body: { mode: "raw", raw: JSON.stringify(exampleFor(schema), null, 2), options: { raw: { language: "json" } } }
    };
  }
  return {
    header: [{ key: "Content-Type", value: contentType }],
    body: { mode: "raw", raw: "{}" }
  };
}

function postResponseScripts(pathName, method) {
  if (pathName === "/api/v1/auth/login" && method === "post") {
    return [{
      listen: "test",
      script: { type: "text/javascript", exec: [
        "const body = pm.response.json();",
        "if (body.accessToken) pm.environment.set('accessToken', body.accessToken);",
        "if (body.refreshToken) pm.environment.set('refreshToken', body.refreshToken);"
      ] }
    }];
  }
  if (pathName === "/api/v1/company/measurements" && method === "post") {
    return [{
      listen: "test",
      script: { type: "text/javascript", exec: [
        "const body = pm.response.json();",
        "if (body.measurement?.id) pm.environment.set('measurementId', body.measurement.id);",
        "if (body.version?.id) pm.environment.set('versionId', body.version.id);"
      ] }
    }];
  }
  return [];
}

const folders = new Map();
for (const [pathName, pathItem] of Object.entries(document.paths)) {
  for (const method of ["get", "post", "put", "patch", "delete", "head", "options"]) {
    const operation = pathItem[method];
    if (!operation) continue;
    const parameters = [...(pathItem.parameters ?? []), ...(operation.parameters ?? [])];
    const convertedPath = pathName.replaceAll(/\{([^}]+)\}/g, "{{$1}}");
    const query = parameters.filter(parameter => parameter.in === "query").map(parameter => ({
      key: parameter.name,
      value: `{{${parameter.name}}}`,
      disabled: parameter.required !== true
    }));
    const body = requestBody(operation);
    const headerParameters = parameters.filter(parameter => parameter.in === "header").map(parameter => ({
      key: parameter.name,
      value: `{{${parameter.name.replaceAll("-", "_")}}}`,
      disabled: parameter.required !== true
    }));
    const request = {
      method: method.toUpperCase(),
      header: [...(body.header ?? []), ...headerParameters],
      url: { raw: `{{baseUrl}}${convertedPath}`, host: ["{{baseUrl}}"], path: convertedPath.split("/").filter(Boolean), query },
      description: operation.description ?? operation.summary ?? ""
    };
    if (body.body) request.body = body.body;
    if (Array.isArray(operation.security) && operation.security.length === 0) request.auth = { type: "noauth" };
    const tag = operation.tags?.[0] ?? "Other";
    const item = {
      name: operation.summary ?? `${method.toUpperCase()} ${pathName}`,
      request,
      response: [],
      event: postResponseScripts(pathName, method)
    };
    if (!folders.has(tag)) folders.set(tag, []);
    folders.get(tag).push(item);
  }
}

const collection = {
  info: {
    name: "Canteiro.io API",
    description: "Coleção gerada do contrato OpenAPI. Inclui todos os controllers e salva tokens do login automaticamente.",
    schema: "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  auth: { type: "bearer", bearer: [{ key: "token", value: "{{accessToken}}", type: "string" }] },
  variable: [
    { key: "baseUrl", value: "http://localhost:8080" },
    { key: "accessToken", value: "" }
  ],
  item: [...folders.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([name, item]) => ({ name, item }))
};

const now = new Date().toISOString();
const environment = {
  id: "8b47236e-1b54-4fa2-96bb-canteiroio001",
  name: "Canteiro.io Local",
  values: [
    ["baseUrl", "http://localhost:8080", true],
    ["accessToken", "", true],
    ["refreshToken", "", true],
    ["workId", "", true],
    ["contractId", "", true],
    ["customerId", "", true],
    ["employeeId", "", true],
    ["measurementId", "", true],
    ["versionId", "", true],
    ["itemId", "", true],
    ["roleId", "", true],
    ["planId", "", true],
    ["featureId", "", true],
    ["bundleId", "", true],
    ["chargeId", "", true],
    ["id", "", true],
    ["module", "MEASUREMENTS", true],
    ["operation", "CREATE", true],
    ["page", "0", true],
    ["size", "20", true],
    ["sort", "createdAt,desc", true],
    ["justification", "Teste via Postman", true],
    ["documentType", "EVIDENCE", true],
    ["asaas_access_token", "", true]
  ].map(([key, value, enabled]) => ({ key, value, type: "default", enabled })),
  _postman_variable_scope: "environment",
  _postman_exported_at: now,
  _postman_exported_using: "Canteiro.io OpenAPI generator"
};

fs.writeFileSync(collectionPath, JSON.stringify(collection, null, 2) + "\n");
fs.writeFileSync(environmentPath, JSON.stringify(environment, null, 2) + "\n");
console.log(`Generated ${[...folders.values()].flat().length} requests in ${folders.size} folders.`);
