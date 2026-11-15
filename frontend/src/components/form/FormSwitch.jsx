import { Controller } from 'react-hook-form';
import { FormControlLabel, Switch } from '@mui/material';

export default function FormSwitch({ name, control, label, ...props }) {
  return (
    <Controller
      name={name}
      control={control}
      render={({ field }) => <FormControlLabel control={<Switch checked={Boolean(field.value)} onChange={(e) => field.onChange(e.target.checked)} {...props} />} label={label} />}
    />
  );
}
