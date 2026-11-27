import { Suspense, lazy } from "react";
import { Toaster } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { Route, Switch, Redirect } from "wouter";
import ErrorBoundary from "./components/ErrorBoundary";
import { ThemeProvider } from "./contexts/ThemeContext";
import { AuthProvider } from "./contexts/AuthContext";
import { useAuth } from "./hooks/useAuth";

const NotFound = lazy(() => import("@/pages/NotFound"));
const Dashboard = lazy(() => import("@/pages/Dashboard"));
const Doctors = lazy(() => import("@/pages/Doctors"));
const Patients = lazy(() => import("@/pages/Patients"));
const Appointments = lazy(() => import("@/pages/Appointments"));
const MedicalRecords = lazy(() => import("@/pages/MedicalRecords"));
const Prescriptions = lazy(() => import("@/pages/Prescriptions"));
const Certificates = lazy(() => import("@/pages/Certificates"));
const Specialties = lazy(() => import("@/pages/Specialties"));
const Insurance = lazy(() => import("@/pages/Insurance"));
const Users = lazy(() => import("@/pages/Users"));
const Availability = lazy(() => import("@/pages/Availability"));
const Audit = lazy(() => import("@/pages/Audit"));
const ClinicalAI = lazy(() => import("@/pages/ClinicalAI"));
const Login = lazy(() => import("@/pages/Login"));

function RouteFallback() {
  return (
    <div className="flex h-screen items-center justify-center bg-background text-sm text-muted-foreground">
      Carregando...
    </div>
  );
}

function AdminRoute({ component: Component }: { component: React.ComponentType }) {
  const { isAuthenticated, user } = useAuth();
  if (!isAuthenticated) return <Redirect to="/login" />;
  if (user?.role !== 'ROLE_ADMIN') return <Redirect to="/" />;
  return <Component />;
}

function DashboardRoute({ component: Component }: { component: React.ComponentType }) {
  const { isAuthenticated, user } = useAuth();
  if (!isAuthenticated) return <Redirect to="/login" />;
  if (user?.role === 'ROLE_ADMIN') return <Redirect to="/users" />;
  if (user?.role === 'ROLE_AUDITOR') return <Redirect to="/audit" />;
  return <Component />;
}

function NonAdminRoute({ component: Component }: { component: React.ComponentType }) {
  const { isAuthenticated, user } = useAuth();
  if (!isAuthenticated) return <Redirect to="/login" />;
  if (user?.role === 'ROLE_ADMIN') return <Redirect to="/users" />;
  return <Component />;
}

function ProntuarioRoute({ component: Component }: { component: React.ComponentType }) {
  const { isAuthenticated, user } = useAuth();
  if (!isAuthenticated) return <Redirect to="/login" />;
  if (user?.role === 'ROLE_ADMIN') return <Redirect to="/users" />;
  if (user?.role !== 'ROLE_MEDICO' && user?.role !== 'ROLE_AUDITOR' && user?.role !== 'ROLE_GESTOR') {
    return <Redirect to="/" />;
  }
  return <Component />;
}

function AuditorRoute({ component: Component }: { component: React.ComponentType }) {
  const { isAuthenticated, user } = useAuth();
  if (!isAuthenticated) return <Redirect to="/login" />;
  if (user?.role !== 'ROLE_AUDITOR' && user?.role !== 'ROLE_GESTOR') return <Redirect to="/" />;
  return <Component />;
}

function MedicoRoute({ component: Component }: { component: React.ComponentType }) {
  const { isAuthenticated, user } = useAuth();
  if (!isAuthenticated) return <Redirect to="/login" />;
  if (user?.role !== 'ROLE_MEDICO') return <Redirect to="/" />;
  return <Component />;
}

function Router() {
  return (
    <Suspense fallback={<RouteFallback />}>
      <Switch>
        <Route path="/login" component={Login} />
        <Route path="/" component={() => <DashboardRoute component={Dashboard} />} />
        <Route path="/users" component={() => <AdminRoute component={Users} />} />
        <Route path="/doctors" component={() => <NonAdminRoute component={Doctors} />} />
        <Route path="/patients" component={() => <NonAdminRoute component={Patients} />} />
        <Route path="/appointments" component={() => <NonAdminRoute component={Appointments} />} />
        <Route path="/medical-records" component={() => <ProntuarioRoute component={MedicalRecords} />} />
        <Route path="/prescriptions" component={() => <NonAdminRoute component={Prescriptions} />} />
        <Route path="/certificates" component={() => <NonAdminRoute component={Certificates} />} />
        <Route path="/specialties" component={() => <NonAdminRoute component={Specialties} />} />
        <Route path="/insurance" component={() => <NonAdminRoute component={Insurance} />} />
        <Route path="/availability" component={() => <NonAdminRoute component={Availability} />} />
        <Route path="/audit" component={() => <AuditorRoute component={Audit} />} />
        <Route path="/clinical-ai" component={() => <MedicoRoute component={ClinicalAI} />} />
        <Route path="/404" component={NotFound} />
        <Route component={NotFound} />
      </Switch>
    </Suspense>
  );
}

function App() {
  return (
    <ErrorBoundary>
      <ThemeProvider defaultTheme="light">
        <AuthProvider>
          <TooltipProvider>
            <Toaster />
            <Router />
          </TooltipProvider>
        </AuthProvider>
      </ThemeProvider>
    </ErrorBoundary>
  );
}

export default App;
